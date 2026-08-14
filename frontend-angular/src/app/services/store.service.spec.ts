import { beforeAll, describe, expect, it, beforeEach, vi } from 'vitest';
import { StoreService } from './store.service';
import { LanguageService } from './language.service';

function createStore(): StoreService {
  const store = new StoreService(
    {} as never,
    {} as never,
    {} as never,
    new LanguageService(),
  );
  store.loggedIn = true;
  return store;
}

describe('StoreService', () => {
  let store: StoreService;

  beforeAll(() => {
    const data: Record<string, string> = {};
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => data[key] ?? null,
      setItem: (key: string, value: string) => {
        data[key] = String(value);
      },
      removeItem: (key: string) => {
        delete data[key];
      },
      clear: () => {
        for (const key in data) delete data[key];
      },
    });
  });

  beforeEach(() => {
    store = createStore();
  });

  describe('formatMoney', () => {
    it('formatea montos con separadores y dos decimales', () => {
      expect(store.formatMoney(1234.5)).toBe('$1,234.50');
    });

    it('formatea cero', () => {
      expect(store.formatMoney(0)).toBe('$0.00');
    });
  });

  describe('paymentLabel', () => {
    it('traduce cada metodo de pago', () => {
      expect(store.paymentLabel('CASH')).toBe('Efectivo');
      expect(store.paymentLabel('TRANSFER')).toBe('Transferencia');
      expect(store.paymentLabel('CARD')).toBe('Tarjeta');
    });
  });

  describe('saleStatusLabel', () => {
    it('traduce cada estado de venta', () => {
      expect(store.saleStatusLabel('PENDING')).toBe('Pendiente');
      expect(store.saleStatusLabel('CONFIRMED')).toBe('Confirmada');
      expect(store.saleStatusLabel('PARTIALLY_REFUNDED')).toBe('Parcialmente devuelta');
      expect(store.saleStatusLabel('CANCELLED')).toBe('Cancelada');
      expect(store.saleStatusLabel('REFUNDED')).toBe('Devuelta');
    });
  });

  describe('customerWhatsappHref', () => {
    it('normaliza el telefono para wa.me', () => {
      expect(store.customerWhatsappHref('+57 300 123 4567')).toBe('https://wa.me/573001234567');
    });

    it('devuelve # si no hay telefono', () => {
      expect(store.customerWhatsappHref('')).toBe('#');
    });
  });

  describe('carrito', () => {
    it('calcula subtotal, descuento y total', () => {
      store.cart = [
        { productId: 1, name: 'Tenis', qty: 2, price: 100 },
        { productId: 2, name: 'Gorra', qty: 1, price: 50 },
      ];
      store.checkoutDiscount = 10;

      expect(store.cartSubtotal).toBe(250);
      expect(store.manualCartDiscount).toBe(10);
      expect(store.cartTotal).toBe(240);
    });

    it('calcula vuelto solo en efectivo', () => {
      store.cart = [{ productId: 1, name: 'Tenis', qty: 1, price: 100 }];
      store.selectedPayment = 'CASH';
      store.cashReceived = 150;

      expect(store.cartChangeDue).toBe(50);

      store.selectedPayment = 'CARD';
      expect(store.cartChangeDue).toBe(0);
    });
  });

  describe('promotionValueLabel', () => {
    it('muestra porcentaje o monto fijo', () => {
      expect(
        store.promotionValueLabel({
          id: '1',
          name: 'Promo',
          code: 'X',
          type: 'PERCENT',
          value: 10,
          minSubtotal: 0,
          customerId: null,
          startsAt: '2026-01-01',
          endsAt: null,
          active: true,
          notes: '',
          createdAt: '2026-01-01T00:00:00Z',
        }),
      ).toBe('10%');

      expect(
        store.promotionValueLabel({
          id: '2',
          name: 'Promo',
          code: 'Y',
          type: 'FIXED',
          value: 500,
          minSubtotal: 0,
          customerId: null,
          startsAt: '2026-01-01',
          endsAt: null,
          active: true,
          notes: '',
          createdAt: '2026-01-01T00:00:00Z',
        }),
      ).toBe('$500.00');
    });
  });
});
