package com.osmar.boutiqueos.customer.loyalty;

import org.springframework.transaction.annotation.Propagation;
import com.osmar.boutiqueos.config.AccountContext;
import com.osmar.boutiqueos.customer.Customer;
import com.osmar.boutiqueos.customer.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class LoyaltyService {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyService.class);
    private static final int POINTS_PER_20_MXN = 1;
    private static final int POINTS_EXPIRY_MONTHS = 12;

    private final LoyaltyTransactionRepository transactionRepository;
    private final LoyaltyRewardRepository rewardRepository;
    private final CustomerRepository customerRepository;
    private final AccountContext accountContext;

    public LoyaltyService(
            LoyaltyTransactionRepository transactionRepository,
            LoyaltyRewardRepository rewardRepository,
            CustomerRepository customerRepository,
            AccountContext accountContext
    ) {
        this.transactionRepository = transactionRepository;
        this.rewardRepository = rewardRepository;
        this.customerRepository = customerRepository;
        this.accountContext = accountContext;
    }

    // REQUIRES_NEW: se llama despues del commit de la venta, cuando ya no hay
    // transaccion propia en curso (ver SaleService.earnLoyaltyPoints).
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int earnPoints(Long customerId, BigDecimal saleTotal, Long saleId) {
        Long accountId = accountContext.requireAccountId();
        Customer customer = customerRepository.findByIdAndAccountId(customerId, accountId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Cliente no encontrado"));

        int points = saleTotal.divide(BigDecimal.valueOf(20), 0, RoundingMode.FLOOR).intValue();
        if (points <= 0) {
            return 0;
        }

        Instant now = Instant.now();
        // Instant no admite sumar meses (lanza "Unsupported unit: Months"): hay que
        // pasar por la fecha en la zona de la tienda. Ese error hacia fallar TODA
        // venta con clienta, porque corria dentro de la transaccion de la venta.
        Instant expiresAt = now.atZone(ZoneId.systemDefault()).plusMonths(POINTS_EXPIRY_MONTHS).toInstant();

        customer.setLoyaltyPoints(customer.getLoyaltyPoints() + points);
        customerRepository.save(customer);

        LoyaltyTransaction transaction = new LoyaltyTransaction();
        transaction.setAccountId(accountId);
        transaction.setCustomerId(customerId);
        transaction.setType(LoyaltyTransaction.TransactionType.EARNED);
        transaction.setPoints(points);
        transaction.setSaleId(saleId);
        transaction.setDescription("Compra #" + saleId + " - $" + saleTotal.setScale(2));
        transaction.setCreatedAt(now);
        transaction.setExpiresAt(expiresAt);
        transactionRepository.save(transaction);

        log.info("Cliente {} acumuló {} puntos (total: {})", customer.getName(), points, customer.getLoyaltyPoints());
        return points;
    }

    @Transactional
    public LoyaltyTransactionResponse redeem(Long customerId, RedeemRequest request) {
        Long accountId = accountContext.requireAccountId();
        Customer customer = customerRepository.findByIdAndAccountId(customerId, accountId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Cliente no encontrado"));

        LoyaltyReward reward = rewardRepository.findById(request.rewardId())
                .filter(r -> r.getAccountId().equals(accountId) && r.isActive())
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Recompensa no encontrada"));

        int totalPointsNeeded = reward.getPointsRequired() * request.quantity();
        if (customer.getLoyaltyPoints() < totalPointsNeeded) {
            throw new ResponseStatusException(CONFLICT,
                    "Puntos insuficientes. Necesitas " + totalPointsNeeded + " pero tienes " + customer.getLoyaltyPoints());
        }

        customer.setLoyaltyPoints(customer.getLoyaltyPoints() - totalPointsNeeded);
        customerRepository.save(customer);

        LoyaltyTransaction transaction = new LoyaltyTransaction();
        transaction.setAccountId(accountId);
        transaction.setCustomerId(customerId);
        transaction.setType(LoyaltyTransaction.TransactionType.REDEEMED);
        transaction.setPoints(-totalPointsNeeded);
        transaction.setRewardId(reward.getId());
        transaction.setDescription("Canje: " + reward.getName() + " x" + request.quantity());
        transaction.setCreatedAt(Instant.now());
        transaction = transactionRepository.save(transaction);

        log.info("Cliente {} canjeó {} puntos por {}", customer.getName(), totalPointsNeeded, reward.getName());
        return LoyaltyTransactionResponse.from(transaction);
    }

    @Transactional
    public LoyaltyTransactionResponse adjust(Long customerId, int points, String reason) {
        Long accountId = accountContext.requireAccountId();
        Customer customer = customerRepository.findByIdAndAccountId(customerId, accountId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Cliente no encontrado"));

        if (customer.getLoyaltyPoints() + points < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "No se pueden restar más puntos de los que tiene el cliente");
        }

        customer.setLoyaltyPoints(customer.getLoyaltyPoints() + points);
        customerRepository.save(customer);

        LoyaltyTransaction transaction = new LoyaltyTransaction();
        transaction.setAccountId(accountId);
        transaction.setCustomerId(customerId);
        transaction.setType(LoyaltyTransaction.TransactionType.ADJUSTED);
        transaction.setPoints(points);
        transaction.setDescription(reason != null ? reason : "Ajuste manual");
        transaction.setCreatedAt(Instant.now());
        transaction = transactionRepository.save(transaction);

        return LoyaltyTransactionResponse.from(transaction);
    }

    @Transactional(readOnly = true)
    public List<LoyaltyTransactionResponse> getHistory(Long customerId) {
        Long accountId = accountContext.requireAccountId();
        return transactionRepository.findByAccountIdAndCustomerIdOrderByCreatedAtDesc(accountId, customerId)
                .stream().map(LoyaltyTransactionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LoyaltyRewardResponse> getActiveRewards() {
        Long accountId = accountContext.requireAccountId();
        return rewardRepository.findByAccountIdAndActiveTrueOrderByNameAsc(accountId)
                .stream().map(LoyaltyRewardResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LoyaltyRewardResponse> getAllRewards() {
        Long accountId = accountContext.requireAccountId();
        return rewardRepository.findByAccountIdOrderByNameAsc(accountId)
                .stream().map(LoyaltyRewardResponse::from).toList();
    }

    @Transactional
    public LoyaltyRewardResponse createReward(LoyaltyRewardRequest request) {
        Long accountId = accountContext.requireAccountId();
        LoyaltyReward reward = new LoyaltyReward();
        reward.setAccountId(accountId);
        reward.setName(request.name().trim());
        reward.setDescription(request.description());
        reward.setPointsRequired(request.pointsRequired());
        reward.setRewardType(request.rewardType());
        reward.setDiscountAmount(request.discountAmount());
        reward.setProductId(request.productId());
        reward.setActive(request.active() != null ? request.active() : true);
        reward = rewardRepository.save(reward);
        return LoyaltyRewardResponse.from(reward);
    }

    @Transactional
    public LoyaltyRewardResponse updateReward(Long rewardId, LoyaltyRewardRequest request) {
        Long accountId = accountContext.requireAccountId();
        LoyaltyReward reward = rewardRepository.findById(rewardId)
                .filter(r -> r.getAccountId().equals(accountId))
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Recompensa no encontrada"));

        reward.setName(request.name().trim());
        reward.setDescription(request.description());
        reward.setPointsRequired(request.pointsRequired());
        reward.setRewardType(request.rewardType());
        reward.setDiscountAmount(request.discountAmount());
        reward.setProductId(request.productId());
        if (request.active() != null) {
            reward.setActive(request.active());
        }
        reward = rewardRepository.save(reward);
        return LoyaltyRewardResponse.from(reward);
    }

    @Transactional
    public void deleteReward(Long rewardId) {
        Long accountId = accountContext.requireAccountId();
        LoyaltyReward reward = rewardRepository.findById(rewardId)
                .filter(r -> r.getAccountId().equals(accountId))
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Recompensa no encontrada"));
        rewardRepository.delete(reward);
    }

    @Scheduled(fixedRate = 86400_000)
    public void expireOldPoints() {
        List<Long> accountIds = customerRepository.findDistinctAccountIds();
        Instant now = Instant.now();
        for (Long accountId : accountIds) {
            int expired = transactionRepository.expireOldTransactions(accountId, now);
            if (expired > 0) {
                log.info("Expired {} loyalty transactions for account {}", expired, accountId);
            }
        }
    }
}
