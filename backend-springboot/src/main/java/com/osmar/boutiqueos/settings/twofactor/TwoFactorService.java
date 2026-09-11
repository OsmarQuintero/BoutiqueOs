package com.osmar.boutiqueos.settings.twofactor;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Verificacion en dos pasos por correo: al entrar desde un dispositivo nuevo, al
 * recuperar la contrasena y al cambiarla.
 *
 * <p>Reglas: codigo de 6 digitos, vence en 10 minutos, 5 intentos y se quema;
 * reenviar solo cada 60 segundos. Al entrar se puede recordar el dispositivo 30
 * dias para que la caja no pida codigo todas las mananas.
 */
@Service
public class TwoFactorService {

    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final Duration DEVICE_TTL = Duration.ofDays(30);

    private final TwoFactorChallengeRepository challengeRepository;
    private final TrustedDeviceRepository trustedDeviceRepository;
    private final TwoFactorMailService mailService;
    private final SecureRandom random = new SecureRandom();

    public TwoFactorService(
            TwoFactorChallengeRepository challengeRepository,
            TrustedDeviceRepository trustedDeviceRepository,
            TwoFactorMailService mailService
    ) {
        this.challengeRepository = challengeRepository;
        this.trustedDeviceRepository = trustedDeviceRepository;
        this.mailService = mailService;
    }

    /** Correo al que se manda el codigo: el usuario si es un correo, si no el de contacto. */
    public static String resolveEmail(String username, String contactEmail) {
        if (looksLikeEmail(username)) return username.trim().toLowerCase(Locale.ROOT);
        if (looksLikeEmail(contactEmail)) return contactEmail.trim().toLowerCase(Locale.ROOT);
        return null;
    }

    public static String mask(String email) {
        if (email == null || !email.contains("@")) return "";
        String[] parts = email.split("@", 2);
        String name = parts[0];
        String visible = name.isEmpty() ? "" : name.substring(0, 1);
        return visible + "***@" + parts[1];
    }

    /** Crea un desafio nuevo (anula los anteriores del mismo proposito) y manda el codigo. */
    public Started start(Long accountId, String email, TwoFactorPurpose purpose) {
        return start(accountId, null, email, purpose);
    }

    /**
     * @param staffUserId cuenta de caja que esta entrando (el codigo va al correo de
     *                    la duena, que asi autoriza cada dispositivo de caja); null = la duena.
     */
    @Transactional
    public Started start(Long accountId, Long staffUserId, String email, TwoFactorPurpose purpose) {
        Instant now = Instant.now();
        for (TwoFactorChallenge open : challengeRepository.findByAccountIdAndPurposeAndUsedAtIsNull(accountId, purpose)) {
            open.setUsedAt(now);
        }
        TwoFactorChallenge challenge = new TwoFactorChallenge();
        challenge.setId(UUID.randomUUID().toString());
        challenge.setAccountId(accountId);
        challenge.setStaffUserId(staffUserId);
        challenge.setPurpose(purpose);
        challenge.setEmail(email);
        String code = newCode();
        challenge.setCodeHash(hash(challenge.getId() + ":" + code));
        challenge.setExpiresAt(now.plus(CODE_TTL));
        challenge.setLastSentAt(now);
        challengeRepository.save(challenge);
        mailService.sendCode(email, code, purpose);
        return new Started(challenge.getId(), mask(email));
    }

    /** Manda un codigo nuevo en el mismo desafio. Maximo uno por minuto. */
    @Transactional
    public Started resend(String challengeId, TwoFactorPurpose purpose) {
        TwoFactorChallenge challenge = challengeRepository.findById(challengeId == null ? "" : challengeId)
                .filter(c -> c.getPurpose() == purpose && c.getUsedAt() == null)
                .orElseThrow(() -> new TwoFactorException("Ese codigo ya no es valido. Vuelve a empezar."));
        Instant now = Instant.now();
        if (challenge.getLastSentAt().plus(RESEND_COOLDOWN).isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Espera un minuto antes de pedir otro codigo");
        }
        String code = newCode();
        challenge.setCodeHash(hash(challenge.getId() + ":" + code));
        challenge.setFailedAttempts(0);
        challenge.setExpiresAt(now.plus(CODE_TTL));
        challenge.setLastSentAt(now);
        challengeRepository.save(challenge);
        mailService.sendCode(challenge.getEmail(), code, purpose);
        return new Started(challenge.getId(), mask(challenge.getEmail()));
    }

    /**
     * Comprueba el codigo de un desafio y lo quema si es correcto.
     * noRollbackFor: el intento fallido se tiene que guardar aunque se lance el error.
     *
     * @return la cuenta a la que pertenece el desafio
     */
    @Transactional(noRollbackFor = TwoFactorException.class)
    public Long verify(String challengeId, String code, TwoFactorPurpose purpose) {
        return verifyChallenge(challengeId, code, purpose).accountId();
    }

    /** Como {@link #verify} pero dice tambien si quien entra es una cuenta de caja. */
    @Transactional(noRollbackFor = TwoFactorException.class)
    public Verified verifyChallenge(String challengeId, String code, TwoFactorPurpose purpose) {
        TwoFactorChallenge challenge = challengeRepository.findById(challengeId == null ? "" : challengeId)
                .filter(c -> c.getPurpose() == purpose)
                .orElseThrow(() -> new TwoFactorException("Ese codigo ya no es valido. Vuelve a empezar."));
        Long accountId = check(challenge, code);
        return new Verified(accountId, challenge.getStaffUserId());
    }

    /** Igual que {@link #verify} pero buscando el ultimo desafio abierto de la cuenta. */
    @Transactional(noRollbackFor = TwoFactorException.class)
    public Long verifyLatest(Long accountId, String code, TwoFactorPurpose purpose) {
        TwoFactorChallenge challenge = challengeRepository
                .findFirstByAccountIdAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(accountId, purpose)
                .orElseThrow(() -> new TwoFactorException("Primero pide un codigo"));
        return check(challenge, code);
    }

    private Long check(TwoFactorChallenge challenge, String code) {
        Instant now = Instant.now();
        if (challenge.getUsedAt() != null) {
            throw new TwoFactorException("Ese codigo ya se uso o se reemplazo por uno nuevo");
        }
        if (challenge.getExpiresAt().isBefore(now)) {
            throw new TwoFactorException("El codigo vencio. Pide uno nuevo.");
        }
        String candidate = code == null ? "" : code.replaceAll("\\s+", "");
        boolean ok = candidate.matches("\\d{6}") && MessageDigest.isEqual(
                hash(challenge.getId() + ":" + candidate).getBytes(StandardCharsets.UTF_8),
                challenge.getCodeHash().getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            int failed = challenge.getFailedAttempts() + 1;
            challenge.setFailedAttempts(failed);
            if (failed >= MAX_FAILED_ATTEMPTS) {
                challenge.setUsedAt(now);
                challengeRepository.save(challenge);
                throw new TwoFactorException("Demasiados intentos. Pide un codigo nuevo.");
            }
            challengeRepository.save(challenge);
            throw new TwoFactorException("Codigo incorrecto. Te quedan " + (MAX_FAILED_ATTEMPTS - failed) + " intentos.");
        }
        challenge.setUsedAt(now);
        challengeRepository.save(challenge);
        return challenge.getAccountId();
    }

    /** Recuerda este navegador 30 dias. Devuelve el token que el navegador debe guardar. */
    public String trustDevice(Long accountId, String label) {
        return trustDevice(accountId, null, label);
    }

    @Transactional
    public String trustDevice(Long accountId, Long staffUserId, String label) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        TrustedDevice device = new TrustedDevice();
        device.setAccountId(accountId);
        device.setStaffUserId(staffUserId);
        device.setTokenHash(hash(token));
        device.setLabel(label == null ? null : label.substring(0, Math.min(200, label.length())));
        device.setExpiresAt(Instant.now().plus(DEVICE_TTL));
        trustedDeviceRepository.save(device);
        return token;
    }

    public boolean isTrustedDevice(Long accountId, String token) {
        return isTrustedDevice(accountId, null, token);
    }

    /** El dispositivo tiene que ser de la misma persona: el de la cajera no sirve a otra cajera. */
    @Transactional
    public boolean isTrustedDevice(Long accountId, Long staffUserId, String token) {
        if (token == null || token.isBlank()) return false;
        return trustedDeviceRepository.findByTokenHash(hash(token.trim()))
                .filter(device -> device.getAccountId().equals(accountId))
                .filter(device -> java.util.Objects.equals(device.getStaffUserId(), staffUserId))
                .filter(device -> device.getExpiresAt().isAfter(Instant.now()))
                .map(device -> {
                    device.setLastUsedAt(Instant.now());
                    trustedDeviceRepository.save(device);
                    return true;
                })
                .orElse(false);
    }

    /** Al cambiar la contrasena, todos los dispositivos vuelven a pedir codigo. */
    @Transactional
    public void forgetDevices(Long accountId) {
        trustedDeviceRepository.deleteAllByAccountId(accountId);
    }

    @Transactional
    public void forgetStaffDevices(Long staffUserId) {
        trustedDeviceRepository.deleteAllByStaffUserId(staffUserId);
    }

    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void cleanup() {
        Instant now = Instant.now();
        challengeRepository.deleteByExpiresAtBefore(now.minus(Duration.ofDays(1)));
        trustedDeviceRepository.deleteByExpiresAtBefore(now);
    }

    private String newCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean looksLikeEmail(String value) {
        return value != null && value.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    }

    public record Started(String challengeId, String maskedEmail) {
    }

    /** @param staffUserId null si quien entro es la duena. */
    public record Verified(Long accountId, Long staffUserId) {
    }
}
