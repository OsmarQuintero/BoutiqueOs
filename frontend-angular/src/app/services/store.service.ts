import { HttpClient, HttpHeaders } from '@angular/common/http';
import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, NgZone, signal } from '@angular/core';
import { finalize, retry, throwError, timeout, timer } from 'rxjs';
import { LanguageService, TranslateParams, AppLang } from './language.service';
import { RefreshService } from './refresh.service';

export type PaymentMethod = 'CASH' | 'TRANSFER' | 'CARD';
export type SaleStatus = 'PENDING' | 'CONFIRMED' | 'PARTIALLY_REFUNDED' | 'CANCELLED' | 'REFUNDED';
export type ProductStatus = 'ACTIVE' | 'OUT_OF_STOCK' | 'ARCHIVED';
export type PromotionType = 'PERCENT' | 'FIXED';
export type TicketPaperSize = 'THERMAL_58' | 'THERMAL_80' | 'HALF_LETTER';

export interface Product {
  id: number;
  name: string;
  category: string;
  size: string;
  color: string;
  sku: string;
  imageUrl: string | null;
  costPrice: number;
  salePrice: number;
  stock: number;
  status: ProductStatus;
}

export interface ProductCategory {
  id: number;
  name: string;
  description: string | null;
  sizeLabel: string;
  active: boolean;
  createdAt: string;
}

interface CategoryPreset {
  name: string;
  description: string;
  sizeLabel: string;
  productHint: string;
}

interface CategoryFormState {
  presetName: string;
  name: string;
  description: string;
  active: boolean;
}

export interface CartItem {
  productId: number;
  name: string;
  qty: number;
  price: number;
}

export interface Customer {
  id: number;
  name: string;
  phone: string;
  notes: string;
  createdAt: string;
}

export interface Promotion {
  id: string;
  name: string;
  code: string;
  type: PromotionType;
  value: number;
  minSubtotal: number;
  customerId: number | null;
  startsAt: string;
  endsAt: string | null;
  active: boolean;
  notes: string;
  createdAt: string;
}

export interface SaleRecordItem {
  id: number;
  productId: number;
  productName: string;
  quantity: number;
  refundedQuantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface SaleRecord {
  id: number;
  subtotal: number;
  discount: number;
  total: number;
  cashReceived: number;
  changeDue: number;
  estimatedProfit: number;
  refundedTotal: number;
  refundedProfit: number;
  paymentMethod: PaymentMethod;
  status: SaleStatus;
  customerId: number | null;
  customerName: string | null;
  createdAt: string;
  refundedAt?: string | null;
  items: SaleRecordItem[];
}

export interface SaleRefundRecordItem {
  id: number;
  saleItemId: number;
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
  total: number;
  estimatedProfit: number;
}

export interface SaleRefundRecord {
  id: number;
  saleId: number;
  paymentMethod: PaymentMethod;
  customerName: string | null;
  total: number;
  estimatedProfit: number;
  createdAt: string;
  items: SaleRefundRecordItem[];
}

export interface DailyCashCount {
  businessDate: string;
  actualCash: number;
  notes: string | null;
  closed: boolean;
  closedAt: string | null;
  updatedAt: string;
}

export type InventoryMovementType = 'PURCHASE' | 'SALE' | 'ADJUSTMENT' | 'RETURN';
export type ReportPanel = 'summary' | 'sales' | 'tickets' | 'refunds' | 'movements' | 'history';
export type ReportIncidentFilter = 'ALL' | 'PENDING' | 'REFUNDS' | 'CANCELLED' | 'ADJUSTMENTS';
export type InventoryPanel = 'summary' | 'purchases';
export type PosSection = 'products' | 'sale' | 'ticket';
export type ProductsSection = 'form' | 'catalog';
export type CatalogSection = 'products';
export type CategoriesSection = 'categories';
export type CustomersSection = 'form' | 'list' | 'history';
export type PromosSection = 'form' | 'list';
export type SettingsSection = 'profile';
export type ViewSectionId =
  | PosSection
  | ProductsSection
  | CatalogSection
  | CategoriesSection
  | CustomersSection
  | PromosSection
  | ReportPanel
  | InventoryPanel
  | SettingsSection;

export interface InventoryMovement {
  id: number;
  productId: number;
  productName: string;
  type: InventoryMovementType;
  quantity: number;
  unitCost: number | null;
  note: string | null;
  createdAt: string;
}

export interface PurchaseRecord {
  id: number;
  supplierName: string | null;
  productId: number;
  productName: string;
  quantity: number;
  unitCost: number;
  totalCost: number;
  note: string | null;
  createdAt: string;
}

export interface AppSettings {
  storeName: string;
  phone: string;
  address: string;
  street: string;
  neighborhood: string;
  city: string;
  postalCode: string;
  contactEmail: string;
  instagramHandle: string;
  socialNetwork: string;
  logoUrl: string;
  thankYouMessage: string;
  ticketPrefix: string;
  ticketFooterNote: string;
  ticketPaperSize: TicketPaperSize;
  showLogoOnTicket: boolean;
  showAddressOnTicket: boolean;
  showPhoneOnTicket: boolean;
  showCustomerOnTicket: boolean;
  showSavingsOnTicket: boolean;
  showChangeOnTicket: boolean;
  autoOpenTicket: boolean;
  username: string;
  updatedAt: string;
}

interface LoginResponse {
  valid: boolean;
  token: string | null;
}

interface PasswordResetRequestResponse {
  accepted: boolean;
}

interface PasswordResetValidateResponse {
  valid: boolean;
  email: string | null;
  expiresAt: string | null;
}

interface PasswordResetConfirmResponse {
  updated: boolean;
  username: string | null;
}

interface OnboardingStartResponse {
  ready: boolean;
  onboardingToken: string;
  email: string | null;
  expiresAt: string;
}

interface OnboardingCompleteResponse {
  completed: boolean;
  username: string;
}

const LOGIN_TIMEOUT_MS = 20000;
const LOGIN_RETRY_COUNT = 3;
const LOGIN_RETRY_DELAY_MS = 5000;
const ONBOARDING_TIMEOUT_MS = 15000;
const SAVE_TIMEOUT_MS = 4000;

export type ViewId =
  | 'pos'
  | 'products'
  | 'catalog'
  | 'categories'
  | 'inventory'
  | 'customers'
  | 'promos'
  | 'reports'
  | 'settings';
export type AlertType = 'success' | 'error' | 'warning' | 'info';

const PROMOS_STORAGE_KEY = 'boutiqueos.promotions.v1';

const OFFLINE_QUEUE_KEY = 'boutiqueos.offline.sales.v1';

interface OfflineSaleEntry {
  id: string;
  payload: {
    paymentMethod: PaymentMethod;
    discount: number;
    cashReceived: number;
    customerId: number | null;
    items: Array<{ productId: number; quantity: number }>;
  };
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class StoreService {
  readonly apiBase = this.resolveApiBase();

  get lang(): AppLang {
    return this.language.lang();
  }

  readonly t: LanguageService['t'];
  readonly toggleLang: () => void;

  private readonly loggedInState = signal(false);
  private readonly loginUserState = signal('');
  private readonly loginPassState = signal('');
  private readonly loginErrorState = signal('');
  private readonly loginLoadingState = signal(false);
  private readonly recoveryOpenState = signal(false);
  private readonly recoveryModeState = signal<'request' | 'confirm'>('request');
  private readonly recoveryUserState = signal('');
  private readonly recoveryTokenState = signal('');
  private readonly recoveryMaskedEmailState = signal('');
  private readonly recoveryPassState = signal('');
  private readonly recoveryConfirmPassState = signal('');
  private readonly recoveryErrorState = signal('');
  private readonly recoveryInfoState = signal('');
  private readonly recoveryLoadingState = signal(false);
  private readonly recoveryTokenCheckingState = signal(false);
  private readonly onboardingActiveState = signal(false);
  private readonly onboardingLoadingState = signal(false);
  private readonly onboardingFinishingState = signal(false);
  private readonly onboardingErrorState = signal('');
  private readonly onboardingInfoState = signal('');
  private readonly onboardingTokenState = signal('');
  private readonly onboardingSessionIdState = signal('');
  private readonly onboardingEmailState = signal('');
  loginEndpoint = this.apiUrl('/settings/login');
  logoutEndpoint = this.apiUrl('/settings/logout');
  passwordResetRequestEndpoint = this.apiUrl('/settings/password-reset/request');
  passwordResetValidateEndpoint = this.apiUrl('/settings/password-reset/validate');
  passwordResetConfirmEndpoint = this.apiUrl('/settings/password-reset/confirm');
  onboardingStartEndpoint = this.apiUrl('/onboarding/start');
  onboardingCompleteEndpoint = this.apiUrl('/onboarding/complete');
  private sessionToken = '';
  isSavingTicketSettings = false;
  isSavingCredentials = false;
  settingsMessage = '';
  credentialsMessage = '';
  ticketQrDataUrl = '';
  private ticketQrRefreshSeq = 0;

  activeView: ViewId = 'pos';
  activeSections: Record<ViewId, ViewSectionId> = {
    pos: 'products',
    products: 'form',
    catalog: 'products',
    categories: 'categories',
    inventory: 'summary',
    customers: 'list',
    promos: 'list',
    reports: 'summary',
    settings: 'profile',
  };
  products: Product[] = [];
  productCategories: ProductCategory[] = [];
  cart: CartItem[] = [];
  salesToday: SaleRecord[] = [];
  salesYesterday: SaleRecord[] = [];
  allSales: SaleRecord[] = [];
  refundsToday: SaleRefundRecord[] = [];
  refundsYesterday: SaleRefundRecord[] = [];
  pendingSales: SaleRecord[] = [];
  reportHistory: DailyCashCount[] = [];
  reportDate = this.todayDateString();
  reportPanel: ReportPanel = 'summary';
  reportIncidentFilter: ReportIncidentFilter = 'ALL';
  selectedPayment: PaymentMethod = 'CASH';
  cashReceived = 0;
  private _statusMessage = '';
  alertMessage = '';
  alertType: AlertType = 'info';
  private alertTimer: ReturnType<typeof setTimeout> | null = null;
  isCharging = false;
  searchTerm = '';
  customers: Customer[] = [];
  promotions: Promotion[] = [];
  inventoryMovements: InventoryMovement[] = [];
  reportInventoryMovements: InventoryMovement[] = [];
  reportInventoryMovementsYesterday: InventoryMovement[] = [];
  recentPurchases: PurchaseRecord[] = [];
  inventoryDate = this.todayDateString();
  inventoryPanel: InventoryPanel = 'summary';
  posCategoryFilter = 'ALL';
  inventoryCategoryFilter = 'ALL';
  selectedCustomerId: number | null = null;
  newCustomerName = '';
  newCustomerPhone = '';
  newCustomerNotes = '';
  editingCustomerId: number | null = null;
  customerSearchTerm = '';
  selectedCustomerHistory: Customer | null = null;
  customerSales: SaleRecord[] = [];
  checkoutDiscount = 0;
  selectedPromoId: string | null = null;
  editingPromoId: string | null = null;
  promoSearchTerm = '';
  lastTicket: SaleRecord | null = null;
  ticketSearchTerm = '';
  refundDrafts: Record<number, Record<number, number>> = {};
  actualCashInput = 0;
  cashCountNotes = '';
  cashCountUpdatedAt: string | null = null;
  reportDayClosed = false;
  reportClosedAt: string | null = null;
  isSavingCashCount = false;
  isClosingReportDay = false;
  isReopeningReportDay = false;
  showProductForm = false;
  editingProductId: number | null = null;
  editingCategoryId: number | null = null;
  purchaseForm = {
    productId: null as number | null,
    supplierName: '',
    quantity: 1,
    unitCost: 0,
    note: '',
  };
  productForm = {
    name: '',
    category: '',
    size: '',
    color: '',
    sku: '',
    imageUrl: '',
    costPrice: 0,
    salePrice: 0,
    stock: 0,
    status: 'ACTIVE' as ProductStatus,
  };
  categoryForm = {
    presetName: 'Tenis',
    name: '',
    description: '',
    active: true,
  };
  promoForm = {
    name: '',
    code: '',
    type: 'PERCENT' as PromotionType,
    value: 10,
    minSubtotal: 0,
    customerId: null as number | null,
    startsAt: this.todayDateString(),
    endsAt: '',
    active: true,
    notes: '',
  };
  productImageFileName = '';
  logoFileName = '';
  settings: AppSettings = {
    storeName: 'Boutique OS',
    phone: '',
    address: '',
    street: '',
    neighborhood: '',
    city: '',
    postalCode: '',
    contactEmail: '',
    instagramHandle: '',
    socialNetwork: 'INSTAGRAM',
    logoUrl: '',
    thankYouMessage: 'Gracias por tu compra',
    ticketPrefix: 'BOS',
    ticketFooterNote: '',
    ticketPaperSize: 'THERMAL_80',
    showLogoOnTicket: true,
    showAddressOnTicket: true,
    showPhoneOnTicket: true,
    showCustomerOnTicket: true,
    showSavingsOnTicket: true,
    showChangeOnTicket: true,
    autoOpenTicket: true,
    username: 'admin',
    updatedAt: '',
  };
  settingsForm = {
    storeName: 'Boutique OS',
    phone: '',
    street: '',
    neighborhood: '',
    city: '',
    postalCode: '',
    contactEmail: '',
    instagramHandle: '',
    socialNetwork: 'INSTAGRAM',
    logoUrl: '',
    address: '',
    thankYouMessage: 'Gracias por tu compra',
    ticketPrefix: 'BOS',
    ticketFooterNote: '',
    ticketPaperSize: 'THERMAL_80' as TicketPaperSize,
    showLogoOnTicket: true,
    showAddressOnTicket: true,
    showPhoneOnTicket: true,
    showCustomerOnTicket: true,
    showSavingsOnTicket: true,
    showChangeOnTicket: true,
    autoOpenTicket: true,
  };
  credentialsForm = {
    username: 'admin',
    currentPassword: '',
    newPassword: '',
  };
  onboardingForm = {
    storeName: '',
    phone: '',
    street: '',
    neighborhood: '',
    city: '',
    postalCode: '',
    email: '',
    password: '',
    confirmPassword: '',
  };

  get paymentMethods(): Array<{ label: string; value: PaymentMethod }> {
    return [
      { label: this.t('payment.CASH'), value: 'CASH' },
      { label: this.t('payment.TRANSFER'), value: 'TRANSFER' },
      { label: this.t('payment.CARD'), value: 'CARD' },
    ];
  }

  get productStatuses(): Array<{ label: string; value: ProductStatus }> {
    return [
      { label: this.t('productStatus.ACTIVE'), value: 'ACTIVE' },
      { label: this.t('productStatus.OUT_OF_STOCK'), value: 'OUT_OF_STOCK' },
      { label: this.t('productStatus.ARCHIVED'), value: 'ARCHIVED' },
    ];
  }

  get promotionTypes(): Array<{ label: string; value: PromotionType }> {
    return [
      { label: this.t('promoType.PERCENT'), value: 'PERCENT' },
      { label: this.t('promoType.FIXED'), value: 'FIXED' },
    ];
  }

  get ticketPaperSizes(): Array<{ label: string; value: TicketPaperSize }> {
    return [
      { label: this.t('paper.THERMAL_58'), value: 'THERMAL_58' },
      { label: this.t('paper.THERMAL_80'), value: 'THERMAL_80' },
      { label: this.t('paper.HALF_LETTER'), value: 'HALF_LETTER' },
    ];
  }

  get reportPanels(): Array<{ id: ReportPanel; label: string }> {
    return [
      { id: 'summary', label: this.t('reports.summary') },
      { id: 'sales', label: this.t('reports.sales') },
      { id: 'tickets', label: this.t('reports.tickets') },
      { id: 'refunds', label: this.t('reports.refunds') },
      { id: 'movements', label: this.t('reports.movements') },
      { id: 'history', label: this.t('reports.history') },
    ];
  }

  get inventoryPanels(): Array<{ id: InventoryPanel; label: string }> {
    return [
      { id: 'summary', label: this.t('reports.summary') },
      { id: 'purchases', label: this.t('inventory.purchases') },
    ];
  }

  readonly categoryPresets: CategoryPreset[] = [
    {
      name: 'Tenis',
      description: 'Calzado casual y deportivo',
      sizeLabel: 'Numero',
      productHint: 'Ej. 26, 27, 28.5',
    },
    {
      name: 'Gorras',
      description: 'Gorras y cachuchas con ajuste por talla o broche',
      sizeLabel: 'Ajuste',
      productHint: 'Ej. Unitalla, Snapback',
    },
    {
      name: 'Accesorios',
      description: 'Bolsos, cinturones, lentes y joyeria',
      sizeLabel: 'Medida',
      productHint: 'Ej. Unitalla, 90 cm',
    },
  ];

  constructor(
    private readonly http: HttpClient,
    private readonly refresh: RefreshService,
    private readonly ngZone: NgZone,
    private readonly language: LanguageService,
  ) {
    this.t = this.language.t;
    this.toggleLang = this.language.toggleLang;
    this._statusMessage = this.t('ok.ready');
    this.loadPromotionsFromStorage();
    if (typeof window !== 'undefined') {
      window.addEventListener('online', () => this.flushOfflineSales());
    }
  }

  get loggedIn(): boolean {
    return this.loggedInState();
  }

  set loggedIn(value: boolean) {
    this.loggedInState.set(value);
  }

  get loginUser(): string {
    return this.loginUserState();
  }

  set loginUser(value: string) {
    this.loginUserState.set(value);
  }

  get loginPass(): string {
    return this.loginPassState();
  }

  set loginPass(value: string) {
    this.loginPassState.set(value);
  }

  get loginError(): string {
    return this.loginErrorState();
  }

  set loginError(value: string) {
    this.loginErrorState.set(value);
  }

  get loginLoading(): boolean {
    return this.loginLoadingState();
  }

  set loginLoading(value: boolean) {
    this.loginLoadingState.set(value);
  }

  get recoveryOpen(): boolean {
    return this.recoveryOpenState();
  }

  set recoveryOpen(value: boolean) {
    this.recoveryOpenState.set(value);
  }

  get recoveryMode(): 'request' | 'confirm' {
    return this.recoveryModeState();
  }

  set recoveryMode(value: 'request' | 'confirm') {
    this.recoveryModeState.set(value);
  }

  get recoveryUser(): string {
    return this.recoveryUserState();
  }

  set recoveryUser(value: string) {
    this.recoveryUserState.set(value);
  }

  get recoveryToken(): string {
    return this.recoveryTokenState();
  }

  set recoveryToken(value: string) {
    this.recoveryTokenState.set(value);
  }

  get recoveryMaskedEmail(): string {
    return this.recoveryMaskedEmailState();
  }

  set recoveryMaskedEmail(value: string) {
    this.recoveryMaskedEmailState.set(value);
  }

  get recoveryPass(): string {
    return this.recoveryPassState();
  }

  set recoveryPass(value: string) {
    this.recoveryPassState.set(value);
  }

  get recoveryConfirmPass(): string {
    return this.recoveryConfirmPassState();
  }

  set recoveryConfirmPass(value: string) {
    this.recoveryConfirmPassState.set(value);
  }

  get recoveryError(): string {
    return this.recoveryErrorState();
  }

  set recoveryError(value: string) {
    this.recoveryErrorState.set(value);
  }

  get recoveryInfo(): string {
    return this.recoveryInfoState();
  }

  set recoveryInfo(value: string) {
    this.recoveryInfoState.set(value);
  }

  get recoveryLoading(): boolean {
    return this.recoveryLoadingState();
  }

  set recoveryLoading(value: boolean) {
    this.recoveryLoadingState.set(value);
  }

  get recoveryTokenChecking(): boolean {
    return this.recoveryTokenCheckingState();
  }

  set recoveryTokenChecking(value: boolean) {
    this.recoveryTokenCheckingState.set(value);
  }

  get onboardingActive(): boolean {
    return this.onboardingActiveState();
  }

  set onboardingActive(value: boolean) {
    this.onboardingActiveState.set(value);
  }

  get onboardingLoading(): boolean {
    return this.onboardingLoadingState();
  }

  set onboardingLoading(value: boolean) {
    this.onboardingLoadingState.set(value);
  }

  get onboardingFinishing(): boolean {
    return this.onboardingFinishingState();
  }

  set onboardingFinishing(value: boolean) {
    this.onboardingFinishingState.set(value);
  }

  get onboardingError(): string {
    return this.onboardingErrorState();
  }

  set onboardingError(value: string) {
    this.onboardingErrorState.set(value);
  }

  get onboardingInfo(): string {
    return this.onboardingInfoState();
  }

  set onboardingInfo(value: string) {
    this.onboardingInfoState.set(value);
  }

  get onboardingToken(): string {
    return this.onboardingTokenState();
  }

  set onboardingToken(value: string) {
    this.onboardingTokenState.set(value);
  }

  get onboardingSessionId(): string {
    return this.onboardingSessionIdState();
  }

  set onboardingSessionId(value: string) {
    this.onboardingSessionIdState.set(value);
  }

  get onboardingEmail(): string {
    return this.onboardingEmailState();
  }

  set onboardingEmail(value: string) {
    this.onboardingEmailState.set(value);
  }

  initializePublicFlow(): void {
    if (typeof window === 'undefined') {
      return;
    }

    const params = new URLSearchParams(window.location.search);
    const sessionId = params.get('session_id') || params.get('checkout_session_id');
    if (sessionId) {
      this.startOnboarding(sessionId.trim());
      return;
    }

    const resetToken = params.get('resetToken');
    if (resetToken) {
      this.startPasswordReset(resetToken.trim());
    }
  }

  currentSessionToken(): string {
    return this.sessionToken;
  }

  handleSessionExpired(): void {
    if (!this.loggedIn && !this.sessionToken) {
      return;
    }
    this.clearSessionState(false);
    this.loginError = this.t('err.sessionExpired');
    this.alertType = 'warning';
    this.showAlert(this.t('err.sessionExpiredAlert'));
  }

  completeOnboarding(): void {
    const storeName = this.onboardingForm.storeName.trim();
    const email = this.onboardingForm.email.trim().toLowerCase();
    const password = this.onboardingForm.password.trim();
    const confirmPassword = this.onboardingForm.confirmPassword.trim();

    if (!this.onboardingToken) {
      this.onboardingError = this.t('err.onboardingSessionInvalid');
      return;
    }
    if (!storeName || !email || !password) {
      this.onboardingError = this.t('err.onboardingIncomplete');
      return;
    }
    if (password.length < 8) {
      this.onboardingError = this.t('err.passwordTooShort');
      return;
    }
    if (password !== confirmPassword) {
      this.onboardingError = this.t('err.passwordsMismatch');
      return;
    }

    this.onboardingError = '';
    this.onboardingFinishing = true;
    this.http
      .post<OnboardingCompleteResponse>(this.onboardingCompleteEndpoint, {
        token: this.onboardingToken,
        storeName,
        phone: this.onboardingForm.phone,
        street: this.onboardingForm.street,
        neighborhood: this.onboardingForm.neighborhood,
        city: this.onboardingForm.city,
        postalCode: this.onboardingForm.postalCode,
        email,
        password,
      })
      .pipe(finalize(() => (this.onboardingFinishing = false)))
      .subscribe({
        next: (result) => {
          if (!result.completed) {
            this.onboardingError = this.t('err.onboardingCompleteFailed');
            return;
          }
          this.loginUser = result.username;
          this.loginPass = '';
          this.onboardingActive = false;
          this.onboardingToken = '';
          this.onboardingSessionId = '';
          this.clearOnboardingQuery();
          this.showAlert(this.t('ok.accountCreated'), 'success');
        },
        error: (error: unknown) => {
          this.onboardingError = this.describeOnboardingError(
            error,
            'No pude completar la activacion.',
          );
        },
      });
  }

  retryOnboardingValidation(): void {
    if (!this.onboardingSessionId || this.onboardingLoading) {
      return;
    }
    this.startOnboarding(this.onboardingSessionId);
  }

  get statusMessage(): string {
    return this._statusMessage;
  }

  set statusMessage(message: string) {
    this._statusMessage = message;
    this.showAlert(message);
  }

  get filteredProducts(): Product[] {
    const q = this.searchTerm.trim().toLowerCase();
    return this.products.filter((p) => {
      const matchesCategory =
        this.posCategoryFilter === 'ALL' || this.sameCategory(p.category, this.posCategoryFilter);
      const matchesSearch =
        !q ||
        p.name.toLowerCase().includes(q) ||
        p.category.toLowerCase().includes(q) ||
        (p.sku && p.sku.toLowerCase().includes(q));
      return matchesCategory && matchesSearch;
    });
  }

  get activeProductCategories(): ProductCategory[] {
    return this.productCategories.filter((category) => category.active);
  }

  get productCategoryOptions(): ProductCategory[] {
    return this.activeProductCategories;
  }

  get currentProductCategory(): ProductCategory | null {
    const selected = this.productForm.category.trim().toLowerCase();
    if (!selected) return null;
    return (
      this.productCategoryOptions.find(
        (category) => category.name.trim().toLowerCase() === selected,
      ) ?? null
    );
  }

  get productSizeLabel(): string {
    return this.currentProductCategory?.sizeLabel || 'Talla';
  }

  get productSizePlaceholder(): string {
    const category = this.currentProductCategory?.name.trim().toLowerCase();
    if (category === 'tenis') return '26, 27, 28';
    if (category === 'gorras') return 'Unitalla, Snapback';
    if (category === 'accesorios') return 'Unitalla, 90 cm';
    return 'S, M, L, 28';
  }

  get categorySummary() {
    return this.productCategories.map((category) => {
      const products = this.products.filter((product) =>
        this.sameCategory(product.category, category.name),
      );
      const stock = products.reduce((sum, product) => sum + product.stock, 0);
      const value = products.reduce((sum, product) => sum + product.stock * product.salePrice, 0);
      return {
        ...category,
        productCount: products.length,
        stock,
        value,
      };
    });
  }

  get selectedCategoryPreset(): CategoryPreset | null {
    return this.findCategoryPreset(this.categoryForm.presetName);
  }

  get categorySizeLabelPreview(): string {
    return this.selectedCategoryPreset?.sizeLabel || 'Talla';
  }

  get categoryProductHintPreview(): string {
    return this.selectedCategoryPreset?.productHint || 'S, M, L, 28';
  }

  get filteredInventoryProducts(): Product[] {
    return this.products.filter(
      (product) =>
        this.inventoryCategoryFilter === 'ALL' ||
        this.sameCategory(product.category, this.inventoryCategoryFilter),
    );
  }

  get filteredPromotions(): Promotion[] {
    const term = this.promoSearchTerm.trim().toLowerCase();
    return [...this.promotions]
      .filter((promo) => {
        if (!term) return true;
        const customerName =
          promo.customerId != null
            ? (this.customers.find((customer) => customer.id === promo.customerId)?.name ?? '')
            : '';
        return [promo.name, promo.code, promo.notes, customerName]
          .filter(Boolean)
          .join(' ')
          .toLowerCase()
          .includes(term);
      })
      .sort((a, b) => {
        if (a.active !== b.active) return a.active ? -1 : 1;
        return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
      });
  }

  get selectedPromo(): Promotion | null {
    return this.promotions.find((promo) => promo.id === this.selectedPromoId) ?? null;
  }

  get activeCartPromo(): Promotion | null {
    const promo = this.selectedPromo;
    return promo && this.isPromotionApplicable(promo) ? promo : null;
  }

  get applicablePromotions(): Promotion[] {
    return this.promotions
      .filter((promo) => this.isPromotionApplicable(promo))
      .sort((a, b) => {
        const discountDiff =
          this.promotionDiscountAmount(b, this.cartSubtotal) -
          this.promotionDiscountAmount(a, this.cartSubtotal);
        if (discountDiff !== 0) return discountDiff;
        return a.name.localeCompare(b.name, 'es-MX');
      });
  }

  get manualCartDiscount(): number {
    return Math.min(Math.max(this.checkoutDiscount || 0, 0), this.cartSubtotal);
  }

  get promoDiscount(): number {
    const promo = this.activeCartPromo;
    if (!promo) return 0;
    return this.promotionDiscountAmount(
      promo,
      Math.max(this.cartSubtotal - this.manualCartDiscount, 0),
    );
  }

  get cartSubtotal(): number {
    return this.cart.reduce((total, item) => total + item.qty * item.price, 0);
  }

  get cartDiscount(): number {
    return Math.min(this.manualCartDiscount + this.promoDiscount, this.cartSubtotal);
  }

  get cartTotal(): number {
    return Math.max(this.cartSubtotal - this.cartDiscount, 0);
  }

  get cartChangeDue(): number {
    return this.selectedPayment === 'CASH'
      ? Math.max((this.cashReceived || 0) - this.cartTotal, 0)
      : 0;
  }

  get todayTotal(): number {
    const confirmedToday = this.salesToday
      .filter((sale) => sale.status !== 'PENDING' && sale.status !== 'CANCELLED')
      .reduce((total, sale) => total + sale.total, 0);
    return confirmedToday - this.refundedTodayTotal;
  }

  get todayProfit(): number {
    const confirmedProfitToday = this.salesToday
      .filter((sale) => sale.status !== 'PENDING' && sale.status !== 'CANCELLED')
      .reduce((total, sale) => total + sale.estimatedProfit, 0);
    return confirmedProfitToday - this.refundedTodayProfit;
  }

  get expectedBoxTotal(): number {
    return this.paymentSummary.reduce((sum, item) => sum + item.total, 0);
  }

  get cashExpected(): number {
    const cashSalesToday = this.salesToday
      .filter(
        (sale) =>
          sale.status !== 'PENDING' && sale.status !== 'CANCELLED' && sale.paymentMethod === 'CASH',
      )
      .reduce((total, sale) => total + sale.total, 0);
    const cashRefundsToday = this.refundedToday
      .filter((refund) => refund.paymentMethod === 'CASH')
      .reduce((total, refund) => total + refund.total, 0);
    return cashSalesToday - cashRefundsToday;
  }

  get cashDifference(): number {
    return (this.actualCashInput || 0) - this.cashExpected;
  }

  get confirmedSalesToday(): SaleRecord[] {
    return this.salesToday.filter(
      (sale) => sale.status !== 'PENDING' && sale.status !== 'CANCELLED',
    );
  }

  get confirmedSalesYesterday(): SaleRecord[] {
    return this.salesYesterday.filter(
      (sale) => sale.status !== 'PENDING' && sale.status !== 'CANCELLED',
    );
  }

  get pendingSalesCount(): number {
    return this.salesToday.filter((sale) => sale.status === 'PENDING').length;
  }

  get cancelledSalesCount(): number {
    return this.salesToday.filter((sale) => sale.status === 'CANCELLED').length;
  }

  get averageTicketToday(): number {
    return this.confirmedSalesToday.length ? this.todayTotal / this.confirmedSalesToday.length : 0;
  }

  get yesterdayTotal(): number {
    const confirmedYesterday = this.salesYesterday
      .filter((sale) => sale.status !== 'PENDING' && sale.status !== 'CANCELLED')
      .reduce((total, sale) => total + sale.total, 0);
    return confirmedYesterday - this.refundsYesterdayTotal;
  }

  get yesterdayProfit(): number {
    const confirmedProfitYesterday = this.salesYesterday
      .filter((sale) => sale.status !== 'PENDING' && sale.status !== 'CANCELLED')
      .reduce((total, sale) => total + sale.estimatedProfit, 0);
    return confirmedProfitYesterday - this.refundsYesterdayProfit;
  }

  get yesterdayPendingSalesCount(): number {
    return this.salesYesterday.filter((sale) => sale.status === 'PENDING').length;
  }

  get piecesSoldToday(): number {
    const sold = this.confirmedSalesToday.reduce(
      (total, sale) => total + sale.items.reduce((sum, item) => sum + item.quantity, 0),
      0,
    );
    const refunded = this.refundedToday.reduce(
      (total, refund) => total + refund.items.reduce((sum, item) => sum + item.quantity, 0),
      0,
    );
    return Math.max(sold - refunded, 0);
  }

  get averageMarginToday(): number {
    return this.todayTotal > 0 ? (this.todayProfit / this.todayTotal) * 100 : 0;
  }

  get inventoryPurchasesTotal(): number {
    return this.recentPurchases.reduce((sum, purchase) => sum + purchase.totalCost, 0);
  }

  get inventoryMovementCounts() {
    return {
      purchases: this.inventoryMovements.filter((movement) => movement.type === 'PURCHASE').length,
      sales: this.inventoryMovements.filter((movement) => movement.type === 'SALE').length,
      adjustments: this.inventoryMovements.filter((movement) => movement.type === 'ADJUSTMENT')
        .length,
      returns: this.inventoryMovements.filter((movement) => movement.type === 'RETURN').length,
    };
  }

  get paymentSummary() {
    return this.paymentMethods.map((method) => {
      const sales = this.salesToday.filter(
        (sale) =>
          sale.status !== 'PENDING' &&
          sale.status !== 'CANCELLED' &&
          sale.paymentMethod === method.value,
      );
      const refunds = this.refundedToday.filter((sale) => sale.paymentMethod === method.value);
      const total =
        sales.reduce((sum, sale) => sum + sale.total, 0) -
        refunds.reduce((sum, sale) => sum + sale.total, 0);
      return {
        ...method,
        total,
        count: sales.length,
        refunds: refunds.length,
        average: sales.length ? total / sales.length : 0,
      };
    });
  }

  get topSellingProductToday(): { name: string; qty: number } | null {
    return this.topProductsToday[0] ?? null;
  }

  get peakHourLabel(): string {
    const buckets = new Map<number, number>();
    for (const sale of this.confirmedSalesToday) {
      const hour = new Date(sale.createdAt).getHours();
      buckets.set(hour, (buckets.get(hour) ?? 0) + sale.total);
    }
    const top = [...buckets.entries()].sort((a, b) => b[1] - a[1])[0];
    if (!top) return 'Sin datos suficientes';
    const start = `${String(top[0]).padStart(2, '0')}:00`;
    const end = `${String((top[0] + 1) % 24).padStart(2, '0')}:00`;
    return `${start} - ${end}`;
  }

  get cashDifferenceSeverity(): 'good' | 'warn' | 'risk' {
    const diff = Math.abs(this.cashDifference);
    if (diff < 0.01) return 'good';
    if (diff < 150) return 'warn';
    return 'risk';
  }

  get reportAlerts(): Array<{ tone: 'good' | 'warn' | 'risk'; title: string; detail: string }> {
    const alerts: Array<{ tone: 'good' | 'warn' | 'risk'; title: string; detail: string }> = [];

    if (this.pendingSalesCount > 0) {
      alerts.push({
        tone: 'warn',
        title: this.t('alert.pendingSales'),
        detail: this.t('alert.pendingSalesDetail', { n: this.pendingSalesCount }),
      });
    }

    if (Math.abs(this.cashDifference) >= 0.01) {
      alerts.push({
        tone: this.cashDifferenceSeverity,
        title: this.t('alert.cashDifference'),
        detail: this.t('alert.cashDifferenceDetail', {
          amount: this.formatMoney(this.cashDifference),
        }),
      });
    }

    if (this.refundedToday.length > 0) {
      alerts.push({
        tone: this.refundedTodayTotal >= 500 ? 'risk' : 'warn',
        title: this.t('alert.refunds'),
        detail: this.t('alert.refundsDetail', {
          n: this.refundedToday.length,
          amount: this.formatMoney(this.refundedTodayTotal),
        }),
      });
    }

    const adjustments = this.reportInventoryMovements.filter(
      (item) => item.type === 'ADJUSTMENT',
    ).length;
    if (adjustments > 0) {
      alerts.push({
        tone: adjustments >= 3 ? 'risk' : 'warn',
        title: this.t('alert.inventoryAdjustments'),
        detail: this.t('alert.inventoryAdjustmentsDetail', { n: adjustments }),
      });
    }

    if (!alerts.length) {
      alerts.push({
        tone: 'good',
        title: this.t('alert.healthyCut'),
        detail: this.t('alert.healthyCutDetail'),
      });
    }

    return alerts;
  }

  get reportComparisonItems(): Array<{
    title: string;
    current: string;
    previous: string;
    detail: string;
    tone: 'good' | 'warn' | 'risk';
  }> {
    return [
      {
        title: this.t('comparison.netSold'),
        current: this.formatMoney(this.todayTotal),
        previous: this.formatMoney(this.yesterdayTotal),
        detail: this.describeMoneyDelta(this.todayTotal, this.yesterdayTotal),
        tone: this.deltaTone(this.todayTotal, this.yesterdayTotal),
      },
      {
        title: this.t('comparison.profit'),
        current: this.formatMoney(this.todayProfit),
        previous: this.formatMoney(this.yesterdayProfit),
        detail: this.describeMoneyDelta(this.todayProfit, this.yesterdayProfit),
        tone: this.deltaTone(this.todayProfit, this.yesterdayProfit),
      },
      {
        title: this.t('comparison.tickets'),
        current: String(this.confirmedSalesToday.length),
        previous: String(this.confirmedSalesYesterday.length),
        detail: this.describeCountDelta(
          this.confirmedSalesToday.length,
          this.confirmedSalesYesterday.length,
          this.t('tickets.ticket'),
          false,
        ),
        tone: this.deltaTone(this.confirmedSalesToday.length, this.confirmedSalesYesterday.length),
      },
      {
        title: this.t('comparison.refunds'),
        current: this.formatMoney(this.refundedTodayTotal),
        previous: this.formatMoney(this.refundsYesterdayTotal),
        detail: this.describeMoneyDelta(this.refundedTodayTotal, this.refundsYesterdayTotal, true),
        tone: this.deltaTone(this.refundedTodayTotal, this.refundsYesterdayTotal, true),
      },
    ];
  }

  get reportIncidentChips(): Array<{
    id: ReportIncidentFilter;
    label: string;
    count: number;
    panel: ReportPanel;
  }> {
    return [
      {
        id: 'PENDING',
        label: this.t('incident.PENDING'),
        count: this.pendingSalesCount,
        panel: 'tickets',
      },
      {
        id: 'REFUNDS',
        label: this.t('incident.REFUNDS'),
        count: this.refundedToday.length,
        panel: 'refunds',
      },
      {
        id: 'CANCELLED',
        label: this.t('incident.CANCELLED'),
        count: this.cancelledSalesCount,
        panel: 'tickets',
      },
      {
        id: 'ADJUSTMENTS',
        label: this.t('incident.ADJUSTMENTS'),
        count: this.reportInventoryMovements.filter((item) => item.type === 'ADJUSTMENT').length,
        panel: 'movements',
      },
    ];
  }

  get topProductsToday() {
    const counts = new Map<string, number>();
    for (const sale of this.salesToday.filter(
      (item) => item.status !== 'PENDING' && item.status !== 'CANCELLED',
    )) {
      for (const item of sale.items) {
        counts.set(item.productName, (counts.get(item.productName) ?? 0) + item.quantity);
      }
    }

    for (const refund of this.refundsToday) {
      for (const item of refund.items) {
        counts.set(item.productName, (counts.get(item.productName) ?? 0) - item.quantity);
      }
    }

    return [...counts.entries()]
      .map(([name, qty]) => ({ name, qty }))
      .filter((item) => item.qty > 0)
      .sort((a, b) => b.qty - a.qty)
      .slice(0, 5);
  }

  get customerHistoryTotal(): number {
    return this.customerSales
      .filter((sale) => sale.status === 'CONFIRMED')
      .reduce((total, sale) => total + sale.total, 0);
  }

  get filteredCustomers(): Customer[] {
    const term = this.customerSearchTerm.trim().toLowerCase();
    if (!term) {
      return [...this.customers].sort((a, b) => a.name.localeCompare(b.name, 'es-MX'));
    }

    return [...this.customers]
      .filter((customer) => {
        const haystack = [customer.name, customer.phone, customer.notes]
          .filter(Boolean)
          .join(' ')
          .toLowerCase();
        return haystack.includes(term);
      })
      .sort((a, b) => a.name.localeCompare(b.name, 'es-MX'));
  }

  get customerConfirmedSalesCount(): number {
    return this.customerSales.filter((sale) => sale.status === 'CONFIRMED').length;
  }

  get customerPendingSalesCount(): number {
    return this.customerSales.filter((sale) => sale.status === 'PENDING').length;
  }

  get customerAverageTicket(): number {
    return this.customerConfirmedSalesCount
      ? this.customerHistoryTotal / this.customerConfirmedSalesCount
      : 0;
  }

  get customerLastPurchase(): SaleRecord | null {
    const sorted = [...this.customerSales].sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    );
    return sorted[0] ?? null;
  }

  get customerPreferredPaymentLabel(): string {
    const counts = new Map<PaymentMethod, number>();
    for (const sale of this.customerSales.filter((item) => item.status !== 'CANCELLED')) {
      counts.set(sale.paymentMethod, (counts.get(sale.paymentMethod) ?? 0) + 1);
    }
    const preferred = [...counts.entries()].sort((a, b) => b[1] - a[1])[0]?.[0];
    return preferred ? this.paymentLabel(preferred) : this.t('customer.NoPreference');
  }

  get customerFavoriteProducts(): Array<{ name: string; qty: number }> {
    const counts = new Map<string, number>();
    for (const sale of this.customerSales.filter((item) => item.status !== 'CANCELLED')) {
      for (const item of sale.items) {
        counts.set(item.productName, (counts.get(item.productName) ?? 0) + item.quantity);
      }
    }

    return [...counts.entries()]
      .map(([name, qty]) => ({ name, qty }))
      .sort((a, b) => b.qty - a.qty)
      .slice(0, 4);
  }

  get customerRelationshipStage(): string {
    if (this.customerHistoryTotal >= 10000 || this.customerConfirmedSalesCount >= 8) {
      return this.t('customerStage.VIP');
    }
    if (this.customerConfirmedSalesCount >= 4) {
      return this.t('customerStage.FREQUENT');
    }
    if (this.customerConfirmedSalesCount >= 1) {
      return this.t('customerStage.ACTIVE');
    }
    return this.t('customerStage.NEW');
  }

  get customerRecencyLabel(): string {
    const lastPurchase = this.customerLastPurchase;
    if (!lastPurchase) {
      return this.t('customerRecency.NONE');
    }

    const diffMs = Date.now() - new Date(lastPurchase.createdAt).getTime();
    const diffDays = Math.max(Math.floor(diffMs / 86400000), 0);
    if (diffDays === 0) return this.t('customerRecency.TODAY');
    if (diffDays === 1) return this.t('customerRecency.YESTERDAY');
    if (diffDays < 7) return this.t('customerRecency.DAYS', { n: diffDays });
    if (diffDays < 30) return this.t('customerRecency.WEEKS', { n: Math.floor(diffDays / 7) });
    return this.t('customerRecency.MONTHS', { n: Math.floor(diffDays / 30) });
  }

  get refundedToday(): SaleRefundRecord[] {
    return this.refundsToday;
  }

  get refundedTodayTotal(): number {
    return this.refundedToday.reduce((total, refund) => total + refund.total, 0);
  }

  get refundedTodayProfit(): number {
    return this.refundedToday.reduce((total, refund) => total + refund.estimatedProfit, 0);
  }

  get refundsYesterdayTotal(): number {
    return this.refundsYesterday.reduce((total, refund) => total + refund.total, 0);
  }

  get refundsYesterdayProfit(): number {
    return this.refundsYesterday.reduce((total, refund) => total + refund.estimatedProfit, 0);
  }

  get filteredSalesToday(): SaleRecord[] {
    return this.salesToday.filter((sale) => this.saleMatchesIncidentFilter(sale));
  }

  get filteredTicketHistory(): SaleRecord[] {
    const term = this.ticketSearchTerm.trim().toLowerCase();
    const base = this.allSales.filter((sale) => this.saleMatchesIncidentFilter(sale));
    if (!term) return base;
    return base.filter((sale) => {
      const productText = sale.items
        .map((item) => item.productName)
        .join(' ')
        .toLowerCase();
      return (
        String(sale.id).includes(term) ||
        (sale.customerName || 'mostrador').toLowerCase().includes(term) ||
        this.paymentLabel(sale.paymentMethod).toLowerCase().includes(term) ||
        this.saleStatusLabel(sale.status).toLowerCase().includes(term) ||
        productText.includes(term)
      );
    });
  }

  get filteredRefundedToday(): SaleRefundRecord[] {
    if (this.reportIncidentFilter !== 'REFUNDS') {
      return this.refundedToday;
    }
    return this.refundedToday;
  }

  get filteredReportInventoryMovements(): InventoryMovement[] {
    if (this.reportIncidentFilter === 'ADJUSTMENTS') {
      return this.reportInventoryMovements.filter((movement) => movement.type === 'ADJUSTMENT');
    }
    return this.reportInventoryMovements;
  }

  get reportIncidentFilterLabel(): string {
    return this.t(`incident.${this.reportIncidentFilter}`);
  }

  get reportHistoryClosedCount(): number {
    return this.reportHistory.filter((item) => item.closed).length;
  }

  get reportHistoryOpenCount(): number {
    return this.reportHistory.filter((item) => !item.closed).length;
  }

  get reportHistoryLastClosedAt(): string | null {
    return this.reportHistory.find((item) => item.closedAt)?.closedAt ?? null;
  }

  setReportPanel(panel: ReportPanel): void {
    this.reportPanel = panel;
    this.activeSections = { ...this.activeSections, reports: panel };
  }

  setReportIncidentFilter(filter: ReportIncidentFilter): void {
    this.reportIncidentFilter = filter;
  }

  focusReportIncident(filter: ReportIncidentFilter, panel: ReportPanel): void {
    this.reportIncidentFilter = filter;
    this.setReportPanel(panel);
  }

  clearReportIncidentFilter(): void {
    this.reportIncidentFilter = 'ALL';
  }

  setInventoryPanel(panel: InventoryPanel): void {
    this.inventoryPanel = panel;
    this.activeSections = { ...this.activeSections, inventory: panel };
  }

  isActiveSection(view: ViewId, section: ViewSectionId): boolean {
    return this.activeView === view && this.activeSections[view] === section;
  }

  sectionLabel(view: ViewId, section: ViewSectionId): string {
    const sectionKey = `${view}.${section}`;
    const label = this.t(`section.${sectionKey}`);
    return label === `section.${sectionKey}` ? '' : label;
  }

  changeReportDate(date: string): void {
    this.reportDate = date || this.todayDateString();
    this.reportIncidentFilter = 'ALL';
    this.refreshReportData();
  }

  shiftReportDate(days: number): void {
    const base = new Date(`${this.reportDate}T00:00:00`);
    base.setDate(base.getDate() + days);
    this.changeReportDate(this.toDateInputValue(base));
  }

  goToTodayReport(): void {
    this.changeReportDate(this.todayDateString());
  }

  openHistoricalReport(date: string, panel: ReportPanel = 'summary'): void {
    this.setReportPanel(panel);
    this.changeReportDate(date);
  }

  changeInventoryDate(date: string): void {
    this.inventoryDate = date || this.todayDateString();
    this.refreshInventoryData();
  }

  shiftInventoryDate(days: number): void {
    const base = new Date(`${this.inventoryDate}T00:00:00`);
    base.setDate(base.getDate() + days);
    this.changeInventoryDate(this.toDateInputValue(base));
  }

  goToTodayInventory(): void {
    this.changeInventoryDate(this.todayDateString());
  }

  refundableQuantity(item: SaleRecordItem): number {
    return Math.max(item.quantity - item.refundedQuantity, 0);
  }

  canRefundSale(sale: SaleRecord): boolean {
    return (
      (sale.status === 'CONFIRMED' || sale.status === 'PARTIALLY_REFUNDED') &&
      sale.items.some((item) => this.refundableQuantity(item) > 0)
    );
  }

  refundQty(saleId: number, item: SaleRecordItem): number {
    return this.refundDrafts[saleId]?.[item.id] ?? 0;
  }

  setRefundQty(saleId: number, item: SaleRecordItem, rawValue: number): void {
    const max = this.refundableQuantity(item);
    const safeValue = Math.min(Math.max(Math.floor(Number(rawValue) || 0), 0), max);
    this.refundDrafts = {
      ...this.refundDrafts,
      [saleId]: {
        ...(this.refundDrafts[saleId] ?? {}),
        [item.id]: safeValue,
      },
    };
  }

  fillRemainingRefund(sale: SaleRecord): void {
    const next: Record<number, number> = {};
    for (const item of sale.items) {
      const remaining = this.refundableQuantity(item);
      if (remaining > 0) next[item.id] = remaining;
    }
    this.refundDrafts = { ...this.refundDrafts, [sale.id]: next };
  }

  clearRefundDraft(saleId: number): void {
    const clone = { ...this.refundDrafts };
    delete clone[saleId];
    this.refundDrafts = clone;
  }

  draftRefundUnits(saleId: number): number {
    return Object.values(this.refundDrafts[saleId] ?? {}).reduce((sum, qty) => sum + qty, 0);
  }

  get pageTitle(): string {
    return this.t(`nav.${this.activeView}`);
  }

  get pageTitleDetail(): string {
    const section = this.activeSections[this.activeView];
    if (!section) return '';
    const label = this.sectionLabel(this.activeView, section);
    return label && label !== this.pageTitle ? label : '';
  }

  get tasks(): string[] {
    const t: string[] = [];
    if (this.pendingSales.length > 0) {
      t.push(this.t('tasks.pendingPayments', { n: this.pendingSales.length }));
    }
    for (const p of this.products.filter((product) => product.stock <= 2)) {
      t.push(this.t('tasks.restock', { name: p.name, stock: p.stock }));
    }
    if (t.length === 0) t.push(this.t('tasks.noNews'));
    return t;
  }

  get stats() {
    return [
      {
        label: this.t('stats.currentSale'),
        value: this.formatMoney(this.cartTotal),
        trend: this.t('stats.lines', { n: this.cart.length }),
      },
      {
        label: this.t('stats.activeProducts'),
        value: String(this.products.filter((product) => product.status !== 'ARCHIVED').length),
        trend: this.t('stats.catalog'),
      },
      {
        label: this.t('stats.pending'),
        value: String(this.pendingSales.length),
        trend: this.pendingSales.length
          ? this.t('stats.pendingConfirm')
          : this.t('stats.noPending'),
      },
      {
        label: this.t('stats.paymentMethod'),
        value: this.paymentLabel(this.selectedPayment),
        trend: this.activeCartPromo
          ? this.t('stats.promo', { code: this.activeCartPromo.code })
          : this.t('stats.selected'),
      },
    ];
  }

  setPosCategoryFilter(category: string): void {
    this.posCategoryFilter = category;
  }

  setInventoryCategoryFilter(category: string): void {
    this.inventoryCategoryFilter = category;
  }

  login(): void {
    const username = this.loginUser.trim();
    const password = this.loginPass.trim();

    if (!username || !password) {
      this.loginError = this.t('err.loginFieldsRequired');
      return;
    }

    this.loginError = '';
    this.loginLoading = true;
    this.refresh
      .track(
        this.t('refresh.signingIn'),
        this.http.post<LoginResponse>(this.loginEndpoint, { username, password }).pipe(
          timeout({ first: LOGIN_TIMEOUT_MS }),
          retry({
            count: LOGIN_RETRY_COUNT,
            delay: (error: unknown, retryCount: number) => {
              const isHttpError =
                error instanceof HttpErrorResponse && error.status >= 400 && error.status < 500;
              if (isHttpError) {
                return throwError(() => error);
              }
              if (retryCount > 1) {
                this.loginError = this.t('warn.serverWaking');
              }
              return timer(LOGIN_RETRY_DELAY_MS);
            },
          }),
        ),
      )
      .pipe(finalize(() => (this.loginLoading = false)))
      .subscribe({
        next: (result) => {
          if (!result.valid || !result.token) {
            this.loginError = this.t('err.invalidCredentials');
            return;
          }

          this.sessionToken = result.token;
          this.loggedIn = true;
          this.loginError = '';
          this.loadSettings();
          this.loadProducts();
          this.loadProductCategories();
          this.loadSalesToday();
          this.loadCustomers();
          this.loadPendingSales();
          this.refreshReportData();
          this.flushOfflineSales();
        },
        error: (error: unknown) => {
          this.sessionToken = '';
          const backendUrl = this.apiBase.replace(/\/api$/, '');
          this.loginError =
            error instanceof HttpErrorResponse && error.status === 429
              ? this.t('err.tooManyAttempts')
              : error instanceof Error && error.name === 'TimeoutError'
                ? this.t('err.backendTimeout', { url: backendUrl })
                : this.t('err.backendUnreachable', { url: backendUrl });
        },
      });
  }

  toggleRecovery(): void {
    const nextValue = !this.recoveryOpen;
    this.recoveryOpen = nextValue;
    if (nextValue) {
      this.recoveryMode = 'request';
      this.recoveryUser = this.loginUser;
      this.recoveryError = '';
      this.recoveryInfo = '';
      return;
    }

    this.clearRecoveryState();
    this.clearPasswordResetQuery();
  }

  requestPasswordReset(): void {
    const username = this.recoveryUser.trim();

    if (!username) {
      this.recoveryError = this.t('err.recoveryUserRequired');
      this.recoveryInfo = '';
      return;
    }

    this.recoveryError = '';
    this.recoveryInfo = '';
    this.recoveryLoading = true;
    this.refresh
      .track(
        this.t('refresh.sendingLink'),
        this.http
          .post<PasswordResetRequestResponse>(this.passwordResetRequestEndpoint, { username })
          .pipe(timeout({ first: LOGIN_TIMEOUT_MS })),
      )
      .pipe(finalize(() => (this.recoveryLoading = false)))
      .subscribe({
        next: () => {
          this.recoveryInfo = this.t('ok.recoveryEmailSent');
          this.recoveryError = '';
        },
        error: (error: unknown) => {
          this.recoveryInfo = '';
          this.recoveryError =
            error instanceof HttpErrorResponse && error.status === 429
              ? this.t('err.tooManyAttempts')
              : error instanceof HttpErrorResponse && error.status === 401
                ? this.t('err.recoveryRoute')
                : this.t('err.recoveryStartFailed');
        },
      });
  }

  completePasswordReset(): void {
    const token = this.recoveryToken.trim();
    const newPassword = this.recoveryPass.trim();
    const confirmPassword = this.recoveryConfirmPass.trim();

    if (!token) {
      this.recoveryError = this.t('err.resetLinkInvalid');
      this.recoveryInfo = '';
      return;
    }

    if (!newPassword || !confirmPassword) {
      this.recoveryError = this.t('err.resetPassRequired');
      this.recoveryInfo = '';
      return;
    }

    if (newPassword !== confirmPassword) {
      this.recoveryError = this.t('err.passwordsMismatch');
      this.recoveryInfo = '';
      return;
    }

    this.recoveryError = '';
    this.recoveryInfo = '';
    this.recoveryLoading = true;
    this.refresh
      .track(
        this.t('refresh.updatingPassword'),
        this.http
          .post<PasswordResetConfirmResponse>(this.passwordResetConfirmEndpoint, {
            token,
            newPassword,
          })
          .pipe(timeout({ first: LOGIN_TIMEOUT_MS })),
      )
      .pipe(finalize(() => (this.recoveryLoading = false)))
      .subscribe({
        next: (result) => {
          this.loginUser = result.username || this.loginUser;
          this.loginPass = '';
          this.recoveryOpen = false;
          this.recoveryMode = 'request';
          this.clearPasswordResetQuery();
          this.recoveryPass = '';
          this.recoveryConfirmPass = '';
          this.recoveryInfo = this.t('ok.passwordUpdated');
          this.recoveryError = '';
          this.showAlert(this.t('ok.passwordUpdated'), 'success');
        },
        error: (error: unknown) => {
          this.recoveryInfo = '';
          this.recoveryError = this.describePasswordResetError(error);
        },
      });
  }

  logout(): void {
    if (this.sessionToken) {
      this.http.post(this.logoutEndpoint, {}, this.authOptions()).subscribe({ error: () => {} });
    }
    this.clearSessionState(true);
  }

  private startOnboarding(sessionId: string): void {
    this.onboardingActive = true;
    this.onboardingLoading = true;
    this.onboardingError = '';
    this.onboardingInfo = this.t('onboarding.checking');
    this.onboardingSessionId = sessionId;

    this.http
      .post<OnboardingStartResponse>(this.onboardingStartEndpoint, { sessionId })
      .pipe(
        timeout({ first: ONBOARDING_TIMEOUT_MS }),
        finalize(() => {
          this.ngZone.run(() => {
            this.onboardingLoading = false;
          });
        }),
      )
      .subscribe({
        next: (result) => {
          this.ngZone.run(() => {
            if (!result.ready || !result.onboardingToken) {
              this.onboardingError = this.t('err.onboardingPrepareFailed');
              return;
            }
            this.onboardingToken = result.onboardingToken;
            this.onboardingEmail = result.email || '';
            this.onboardingForm.email = result.email || '';
            this.onboardingInfo = this.t('ok.onboardingReady');
            this.clearOnboardingQuery();
          });
        },
        error: (error: unknown) => {
          this.ngZone.run(() => {
            this.onboardingError = this.describeOnboardingError(
              error,
              this.t('err.onboardingStripeFailed'),
            );
          });
        },
      });
  }

  private startPasswordReset(token: string): void {
    this.recoveryOpen = true;
    this.recoveryMode = 'confirm';
    this.recoveryToken = token;
    this.recoveryMaskedEmail = '';
    this.recoveryPass = '';
    this.recoveryConfirmPass = '';
    this.recoveryError = '';
    this.recoveryInfo = '';
    this.recoveryTokenChecking = true;

    this.http
      .get<PasswordResetValidateResponse>(this.passwordResetValidateEndpoint, {
        params: { token },
      })
      .pipe(
        timeout({ first: LOGIN_TIMEOUT_MS }),
        finalize(() => (this.recoveryTokenChecking = false)),
      )
      .subscribe({
        next: (result) => {
          if (!result.valid) {
            this.recoveryError = this.t('err.resetLinkInvalid');
            return;
          }
          this.recoveryMaskedEmail = result.email || '';
          this.recoveryInfo = this.t('ok.createNewPassword');
        },
        error: (error: unknown) => {
          this.recoveryError = this.describePasswordResetError(error);
        },
      });
  }

  setView(view: ViewId, section?: ViewSectionId): void {
    this.refresh.flash(this.t('refresh.loadingView', { view: this.viewLabel(view) }));
    this.activeView = view;
    if (section) {
      this.activeSections = { ...this.activeSections, [view]: section };
    }
    if (view === 'reports') {
      this.reportPanel = this.activeSections.reports as ReportPanel;
    }
    if (view === 'inventory') {
      this.inventoryPanel = this.activeSections.inventory as InventoryPanel;
    }
    if (view === 'reports' || view === 'pos') this.loadSalesToday();
    if (view === 'reports') {
      this.refreshReportData();
    }
    if (view === 'settings') {
      this.loadSettings();
    }
    if (view === 'inventory') {
      this.refreshInventoryData();
    }
    if (view === 'catalog') {
      this.loadProductCategories();
      this.loadProducts();
    }
    if (view === 'categories') {
      this.loadProductCategories();
      this.loadProducts();
    }
    if (view === 'customers') this.loadCustomers();
    if (view === 'promos') this.loadCustomers();
    if (view !== 'pos') this.searchTerm = '';
  }

  newSale(): void {
    this.cart = [];
    this.selectedCustomerId = null;
    this.selectedPromoId = null;
    this.checkoutDiscount = 0;
    this.cashReceived = 0;
    this.lastTicket = null;
    this.statusMessage = this.t('ok.ready');
    this.setView('pos', 'sale');
  }

  addToCart(product: Product): void {
    if (product.status === 'ARCHIVED') {
      this.statusMessage = this.t('err.productArchived', { name: product.name });
      return;
    }
    if (product.stock <= this.quantityInCart(product.id)) {
      this.statusMessage = this.t('err.outOfStock', { name: product.name });
      return;
    }
    const current = this.cart.find((item) => item.productId === product.id);
    if (current) {
      current.qty += 1;
    } else {
      this.cart = [
        ...this.cart,
        { productId: product.id, name: product.name, qty: 1, price: product.salePrice },
      ];
    }
    this.statusMessage = this.t('ok.addedToCart', { name: product.name });
  }

  removeFromCart(productId: number): void {
    this.cart = this.cart
      .map((item) => (item.productId === productId ? { ...item, qty: item.qty - 1 } : item))
      .filter((item) => item.qty > 0);
    this.statusMessage = this.t('ok.cartUpdated');
  }

  clearCart(): void {
    this.cart = [];
    this.selectedPromoId = null;
    this.checkoutDiscount = 0;
    this.cashReceived = 0;
    this.statusMessage = this.t('ok.cartCleared');
  }

  selectPayment(method: PaymentMethod): void {
    this.selectedPayment = method;
    this.statusMessage = this.t('ok.methodSelected', { method: this.paymentLabel(method) });
  }

  selectCustomer(customerId: number | null): void {
    this.selectedCustomerId = customerId;
    this.syncSelectedPromo();
    const customerName = customerId
      ? (this.customers.find((customer) => customer.id === customerId)?.name ??
        this.t('pos.counter'))
      : this.t('pos.counter');
    this.statusMessage = this.t('ok.customerSelected', { name: customerName });
  }

  applyPromo(promoId: string | null): void {
    if (!promoId) {
      this.selectedPromoId = null;
      this.statusMessage = this.t('ok.promoRemoved');
      return;
    }

    const promo = this.promotions.find((item) => item.id === promoId);
    if (!promo) {
      this.selectedPromoId = null;
      this.statusMessage = this.t('err.promoMissing');
      return;
    }
    if (!this.isPromotionApplicable(promo)) {
      this.selectedPromoId = null;
      this.statusMessage = this.t('warn.promoNotApplicable');
      return;
    }

    this.selectedPromoId = promo.id;
    this.statusMessage = this.t('ok.promoApplied', { name: promo.name });
  }

  checkout(): void {
    if (!this.cart.length || this.isCharging) {
      this.statusMessage = this.t('warn.addProductsFirst');
      return;
    }

    this.isCharging = true;
    this.refresh
      .track(
        this.t('refresh.processingSale'),
        this.http.post<SaleRecord>(this.apiUrl('/sales'), {
          paymentMethod: this.selectedPayment,
          discount: this.cartDiscount,
          cashReceived: this.selectedPayment === 'CASH' ? this.cashReceived : 0,
          customerId: this.selectedCustomerId,
          items: this.cart.map((item) => ({ productId: item.productId, quantity: item.qty })),
        }),
      )
      .subscribe({
        next: (sale) => {
          this.cart = [];
          this.selectedCustomerId = null;
          this.selectedPromoId = null;
          this.checkoutDiscount = 0;
          this.cashReceived = 0;
          this.lastTicket = sale;
          this.activeSections = { ...this.activeSections, pos: 'ticket' };
          if (this.settings.autoOpenTicket) {
            void this.openTicketPdf(sale);
          }
          this.statusMessage =
            sale.status === 'PENDING'
              ? this.t('warn.paymentPending', {
                  method: this.paymentLabel(sale.paymentMethod),
                  suffix: this.settings.autoOpenTicket ? this.t('warn.ticketOpenSuffix') : '',
                })
              : this.settings.autoOpenTicket
                ? this.t('ok.saleChargedOpen')
                : this.t('ok.saleCharged');
          this.isCharging = false;
          this.loadProducts();
          this.loadSalesToday();
          this.loadPendingSales();
          this.refreshReportData();
        },
        error: (error: unknown) => {
          if (this.shouldQueueOffline(error)) {
            this.queueOfflineSale({
              paymentMethod: this.selectedPayment,
              discount: this.cartDiscount,
              cashReceived: this.selectedPayment === 'CASH' ? this.cashReceived : 0,
              customerId: this.selectedCustomerId,
              items: this.cart.map((item) => ({ productId: item.productId, quantity: item.qty })),
            });
            this.cart = [];
            this.selectedCustomerId = null;
            this.selectedPromoId = null;
            this.checkoutDiscount = 0;
            this.cashReceived = 0;
            this.isCharging = false;
            this.statusMessage = this.t('warn.offlineQueued');
            return;
          }
          this.statusMessage = this.t('err.checkoutFailed');
          this.isCharging = false;
        },
      });
  }

  confirmSale(id: number): void {
    this.refresh
      .track(
        this.t('refresh.confirmingPayment'),
        this.http.post<SaleRecord>(this.apiUrl(`/sales/${id}/confirm`), {}),
      )
      .subscribe({
        next: (sale) => {
          this.statusMessage = this.t('ok.paymentConfirmed');
          this.lastTicket = sale;
          this.loadSalesToday();
          this.loadPendingSales();
          this.refreshReportData();
        },
        error: () => {
          this.statusMessage = this.t('err.paymentConfirmFailed');
        },
      });
  }

  cancelSale(id: number): void {
    this.refresh
      .track(
        this.t('refresh.cancellingSale'),
        this.http.post<SaleRecord>(this.apiUrl(`/sales/${id}/cancel`), {}),
      )
      .subscribe({
        next: () => {
          this.statusMessage = this.t('ok.saleCancelled');
          this.loadProducts();
          this.loadSalesToday();
          this.loadPendingSales();
          this.refreshReportData();
        },
        error: () => {
          this.statusMessage = this.t('err.saleCancelFailed');
        },
      });
  }

  refundSale(id: number): void {
    const draft = this.refundDrafts[id] ?? {};
    const items = Object.entries(draft)
      .map(([saleItemId, quantity]) => ({ saleItemId: Number(saleItemId), quantity }))
      .filter((item) => item.quantity > 0);

    if (!items.length) {
      this.statusMessage = this.t('warn.selectRefundUnits');
      return;
    }

    this.refresh
      .track(
        this.t('refresh.processingRefund'),
        this.http.post<SaleRecord>(this.apiUrl(`/sales/${id}/refund`), { items }),
      )
      .subscribe({
        next: (sale) => {
          this.statusMessage =
            sale.status === 'REFUNDED'
              ? this.t('ok.saleRefunded', { id: sale.id })
              : this.t('ok.salePartialRefunded', { id: sale.id });
          this.lastTicket = sale;
          this.clearRefundDraft(id);
          this.loadProducts();
          this.loadSalesToday();
          this.loadPendingSales();
          this.refreshReportData();
        },
        error: () => {
          this.statusMessage = this.t('err.refundFailed');
        },
      });
  }

  refundAllRemaining(id: number): void {
    this.refresh
      .track(
        this.t('refresh.processingRefund'),
        this.http.post<SaleRecord>(this.apiUrl(`/sales/${id}/refund`), null),
      )
      .subscribe({
        next: (sale) => {
          this.statusMessage =
            sale.status === 'REFUNDED'
              ? this.t('ok.saleRefunded', { id: sale.id })
              : this.t('ok.salePartialRefunded', { id: sale.id });
          this.lastTicket = sale;
          this.clearRefundDraft(id);
          this.loadProducts();
          this.loadSalesToday();
          this.loadPendingSales();
          this.refreshReportData();
        },
        error: () => {
          this.statusMessage = this.t('err.refundFailed');
        },
      });
  }

  createProduct(): void {
    if (!this.productForm.name.trim()) {
      this.statusMessage = this.t('err.productNameRequired');
      return;
    }
    const payload = {
      ...this.productForm,
      imageUrl: this.productForm.imageUrl || null,
    };

    const request = this.editingProductId
      ? this.http.put<Product>(this.apiUrl(`/products/${this.editingProductId}`), payload)
      : this.http.post<Product>(this.apiUrl('/products'), payload);

    this.refresh
      .track(
        this.editingProductId ? this.t('refresh.updatingProduct') : this.t('refresh.savingProduct'),
        request,
      )
      .subscribe({
        next: () => {
          this.statusMessage = this.editingProductId
            ? this.t('ok.productUpdated')
            : this.t('ok.productCreated');
          this.closeProductForm();
          this.loadProducts();
        },
        error: () => {
          this.statusMessage = this.editingProductId
            ? this.t('err.productUpdateFailed')
            : this.t('err.productCreateFailed');
        },
      });
  }

  createCategory(): void {
    if (!this.categoryForm.name.trim()) {
      this.statusMessage = this.t('err.categoryNameRequired');
      return;
    }

    const payload = {
      name: this.categoryForm.name.trim(),
      description: this.categoryForm.description.trim() || null,
      sizeLabel: this.categorySizeLabelPreview,
      active: this.categoryForm.active,
    };

    const request = this.editingCategoryId
      ? this.http.put<ProductCategory>(
          this.apiUrl(`/product-categories/${this.editingCategoryId}`),
          payload,
        )
      : this.http.post<ProductCategory>(this.apiUrl('/product-categories'), payload);

    this.refresh
      .track(
        this.editingCategoryId
          ? this.t('refresh.updatingCategory')
          : this.t('refresh.savingCategory'),
        request,
      )
      .subscribe({
        next: () => {
          this.statusMessage = this.editingCategoryId
            ? this.t('ok.categoryUpdated')
            : this.t('ok.categoryCreated');
          this.resetCategoryForm();
          this.loadProductCategories();
        },
        error: () => {
          this.statusMessage = this.editingCategoryId
            ? this.t('err.categoryUpdateFailed')
            : this.t('err.categoryCreateFailed');
        },
      });
  }

  editCategory(category: ProductCategory): void {
    this.editingCategoryId = category.id;
    this.activeSections = { ...this.activeSections, categories: 'categories' };
    this.categoryForm = {
      presetName: this.inferCategoryPresetName(category),
      name: category.name,
      description: category.description || '',
      active: category.active,
    };
    this.setView('categories', 'categories');
  }

  cancelCategoryEdit(): void {
    this.resetCategoryForm();
  }

  deleteCategory(category: ProductCategory): void {
    if (!window.confirm(this.t('confirm.deleteCategory', { name: category.name }))) return;
    this.refresh
      .track(
        this.t('refresh.deletingCategory'),
        this.http.delete(this.apiUrl(`/product-categories/${category.id}`)),
      )
      .subscribe({
        next: () => {
          this.statusMessage = this.t('ok.categoryDeleted');
          if (this.productForm.category === category.name) {
            this.productForm.category = '';
          }
          this.loadProductCategories();
        },
        error: (error: HttpErrorResponse) => {
          this.statusMessage =
            error.status === 409
              ? this.t('err.categoryInUse')
              : error.status === 404
                ? this.t('err.categoryMissing')
                : this.t('err.categoryDeleteFailed');
        },
      });
  }

  categoryUsageCount(name: string): number {
    return this.products.filter(
      (product) => (product.category || '').trim().toLowerCase() === name.trim().toLowerCase(),
    ).length;
  }

  applyCategoryToProduct(name: string): void {
    this.showProductForm = true;
    this.productForm.category = name;
    this.statusMessage = this.t('ok.categorySelected', { name });
    this.setView('catalog', 'products');
  }

  applyCategoryPreset(preset: {
    name: string;
    description: string | null;
    sizeLabel: string;
  }): void {
    const existing = this.productCategories.find((category) =>
      this.sameCategory(category.name, preset.name),
    );
    if (existing) {
      this.applyCategoryToProduct(existing.name);
      return;
    }

    this.editingCategoryId = null;
    this.categoryForm = {
      presetName: preset.name,
      name: preset.name,
      description: preset.description || '',
      active: true,
    };
    this.productForm.category = preset.name;
    this.statusMessage = this.t('ok.presetLoaded', { name: preset.name });
    this.setView('categories', 'categories');
  }

  selectCategoryPreset(presetName: string): void {
    this.categoryForm.presetName = presetName;
    const preset = this.findCategoryPreset(presetName);
    if (!preset) return;
    if (
      !this.categoryForm.name.trim() ||
      this.findCategoryPreset(this.categoryForm.name)?.name === this.categoryForm.name
    ) {
      this.categoryForm.name = preset.name;
    }
    if (!this.categoryForm.description.trim()) {
      this.categoryForm.description = preset.description;
    }
  }

  editProduct(product: Product): void {
    this.showProductForm = true;
    this.editingProductId = product.id;
    this.productForm = {
      name: product.name,
      category: product.category || '',
      size: product.size || '',
      color: product.color || '',
      sku: product.sku || '',
      imageUrl: product.imageUrl || '',
      costPrice: product.costPrice,
      salePrice: product.salePrice,
      stock: product.stock,
      status: product.status,
    };
    this.productImageFileName = product.imageUrl ? this.t('products.imageLoaded') : '';
    this.setView('catalog', 'products');
  }

  onProductImageSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      this.productForm.imageUrl = typeof reader.result === 'string' ? reader.result : '';
      this.productImageFileName = file.name;
      this.statusMessage = this.t('ok.imageReady', { name: file.name });
    };
    reader.onerror = () => {
      this.statusMessage = this.t('err.imageReadFailed');
    };
    reader.readAsDataURL(file);
  }

  clearProductImage(): void {
    this.productForm.imageUrl = '';
    this.productImageFileName = '';
    this.statusMessage = this.t('ok.imageCleared');
  }

  openProductForm(): void {
    this.showProductForm = true;
    this.resetProductForm();
  }

  closeProductForm(): void {
    this.showProductForm = false;
    this.resetProductForm();
  }

  cancelProductEdit(): void {
    this.closeProductForm();
  }

  deleteProduct(product: Product): void {
    this.refresh
      .track(
        this.t('refresh.deletingProduct'),
        this.http.delete(this.apiUrl(`/products/${product.id}`)),
      )
      .subscribe({
        next: () => {
          this.statusMessage = this.t('ok.productDeleted', { name: product.name });
          if (this.editingProductId === product.id) {
            this.resetProductForm();
          }
          this.loadProducts();
        },
        error: (error: HttpErrorResponse) => {
          this.statusMessage =
            error.status === 404 ? this.t('err.productMissing') : this.t('err.productDeleteFailed');
        },
      });
  }

  restock(product: Product, quantity: number): void {
    this.adjustInventory(
      product.id,
      quantity,
      `Ajuste manual para ${product.name}`,
      this.t('ok.stockUpdated'),
    );
  }

  reduceStock(product: Product): void {
    this.adjustInventory(
      product.id,
      -1,
      `Ajuste manual para ${product.name}`,
      this.t('ok.inventoryAdjustSaved'),
    );
  }

  registerPurchase(): void {
    if (!this.purchaseForm.productId) {
      this.statusMessage = this.t('warn.selectProduct');
      return;
    }
    if (this.purchaseForm.quantity < 1) {
      this.statusMessage = this.t('err.purchaseMinUnits');
      return;
    }

    this.refresh
      .track(
        this.t('refresh.registeringPurchase'),
        this.http.post<PurchaseRecord>(this.apiUrl('/purchases'), {
          productId: this.purchaseForm.productId,
          supplierName: this.purchaseForm.supplierName || null,
          quantity: this.purchaseForm.quantity,
          unitCost: this.purchaseForm.unitCost,
          note: this.purchaseForm.note || null,
        }),
      )
      .subscribe({
        next: () => {
          this.statusMessage = this.t('ok.purchaseRegistered');
          this.purchaseForm = {
            productId: null,
            supplierName: '',
            quantity: 1,
            unitCost: 0,
            note: '',
          };
          this.loadProducts();
          this.refreshInventoryData();
        },
        error: () => {
          this.statusMessage = this.t('err.purchaseFailed');
        },
      });
  }

  addCustomer(): void {
    if (!this.newCustomerName.trim()) {
      this.statusMessage = this.t('err.customerNameRequired');
      return;
    }
    const payload = {
      name: this.newCustomerName.trim(),
      phone: this.newCustomerPhone.trim() || null,
      notes: this.newCustomerNotes.trim() || null,
    };
    const request = this.editingCustomerId
      ? this.http.put<Customer>(this.apiUrl(`/customers/${this.editingCustomerId}`), payload)
      : this.http.post<Customer>(this.apiUrl('/customers'), payload);

    this.refresh
      .track(
        this.editingCustomerId
          ? this.t('refresh.updatingCustomer')
          : this.t('refresh.savingCustomer'),
        request,
      )
      .subscribe({
        next: () => {
          this.statusMessage = this.editingCustomerId
            ? this.t('ok.customerUpdated')
            : this.t('ok.customerAdded');
          this.resetCustomerForm();
          this.loadCustomers();
        },
        error: () => {
          this.statusMessage = this.editingCustomerId
            ? this.t('err.customerUpdateFailed')
            : this.t('err.customerAddFailed');
        },
      });
  }

  editCustomer(customer: Customer): void {
    this.editingCustomerId = customer.id;
    this.newCustomerName = customer.name;
    this.newCustomerPhone = customer.phone || '';
    this.newCustomerNotes = customer.notes || '';
  }

  cancelCustomerEdit(): void {
    this.resetCustomerForm();
  }

  savePromo(): void {
    const name = this.promoForm.name.trim();
    const code = this.normalizePromoCode(this.promoForm.code || name);
    const value = Math.max(Number(this.promoForm.value) || 0, 0);
    const minSubtotal = Math.max(Number(this.promoForm.minSubtotal) || 0, 0);

    if (!name) {
      this.statusMessage = this.t('err.promoNameRequired');
      return;
    }
    if (!code) {
      this.statusMessage = this.t('err.promoCodeRequired');
      return;
    }
    if (value <= 0) {
      this.statusMessage = this.t('err.promoValueRequired');
      return;
    }
    if (this.promoForm.type === 'PERCENT' && value > 100) {
      this.statusMessage = this.t('err.promoPercentMax');
      return;
    }
    if (
      this.promoForm.endsAt &&
      this.promoForm.startsAt &&
      this.promoForm.endsAt < this.promoForm.startsAt
    ) {
      this.statusMessage = this.t('err.promoDateRange');
      return;
    }
    if (
      this.promotions.some(
        (promo) => promo.id !== this.editingPromoId && promo.code.toUpperCase() === code,
      )
    ) {
      this.statusMessage = this.t('err.promoCodeExists');
      return;
    }

    const nextPromo: Promotion = {
      id: this.editingPromoId ?? this.generatePromoId(),
      name,
      code,
      type: this.promoForm.type,
      value,
      minSubtotal,
      customerId: this.promoForm.customerId,
      startsAt: this.promoForm.startsAt || this.todayDateString(),
      endsAt: this.promoForm.endsAt || null,
      active: this.promoForm.active,
      notes: this.promoForm.notes.trim(),
      createdAt:
        this.promotions.find((promo) => promo.id === this.editingPromoId)?.createdAt ??
        new Date().toISOString(),
    };

    this.promotions = this.promotions.some((promo) => promo.id === nextPromo.id)
      ? this.promotions.map((promo) => (promo.id === nextPromo.id ? nextPromo : promo))
      : [nextPromo, ...this.promotions];
    this.persistPromotions();
    this.syncSelectedPromo();
    this.statusMessage = this.editingPromoId ? this.t('ok.promoUpdated') : this.t('ok.promoSaved');
    this.resetPromoForm();
    this.activeSections = { ...this.activeSections, promos: 'list' };
  }

  editPromo(promo: Promotion): void {
    this.editingPromoId = promo.id;
    this.promoForm = {
      name: promo.name,
      code: promo.code,
      type: promo.type,
      value: promo.value,
      minSubtotal: promo.minSubtotal,
      customerId: promo.customerId,
      startsAt: promo.startsAt,
      endsAt: promo.endsAt || '',
      active: promo.active,
      notes: promo.notes,
    };
    this.setView('promos', 'form');
  }

  cancelPromoEdit(): void {
    this.resetPromoForm();
  }

  deletePromo(promo: Promotion): void {
    if (!window.confirm(this.t('confirm.deletePromo', { name: promo.name }))) return;
    this.promotions = this.promotions.filter((item) => item.id !== promo.id);
    if (this.selectedPromoId === promo.id) {
      this.selectedPromoId = null;
    }
    if (this.editingPromoId === promo.id) {
      this.resetPromoForm();
    }
    this.persistPromotions();
    this.statusMessage = this.t('ok.promoDeleted');
  }

  deleteCustomer(customer: Customer): void {
    this.refresh
      .track(
        this.t('refresh.deletingCustomer'),
        this.http.delete(this.apiUrl(`/customers/${customer.id}`)),
      )
      .subscribe({
        next: () => {
          this.statusMessage = this.t('ok.customerDeleted', { name: customer.name });
          if (this.selectedCustomerId === customer.id) this.selectedCustomerId = null;
          if (this.selectedCustomerHistory?.id === customer.id) this.selectedCustomerHistory = null;
          if (this.editingCustomerId === customer.id) this.resetCustomerForm();
          this.loadCustomers();
        },
        error: (error: HttpErrorResponse) => {
          this.statusMessage =
            error.status === 404
              ? this.t('err.customerMissing')
              : this.t('err.customerDeleteFailed');
        },
      });
  }

  showCustomerSales(customer: Customer): void {
    this.selectedCustomerHistory = customer;
    this.activeSections = { ...this.activeSections, customers: 'history' };
    this.refresh
      .track(
        this.t('refresh.loadingHistory'),
        this.http.get<SaleRecord[]>(this.apiUrl(`/sales/customer/${customer.id}`)),
      )
      .subscribe({
        next: (sales) => {
          this.customerSales = sales;
        },
        error: () => {
          this.customerSales = [];
        },
      });
  }

  clearCustomerHistory(): void {
    this.selectedCustomerHistory = null;
    this.customerSales = [];
    this.activeSections = { ...this.activeSections, customers: 'list' };
  }

  clearTicket(): void {
    this.lastTicket = null;
  }

  saveTicketSettings(): void {
    if (!this.settingsForm.storeName.trim()) {
      this.settingsMessage = this.t('err.storeNameRequired');
      this.statusMessage = this.settingsMessage;
      return;
    }

    this.isSavingTicketSettings = true;
    this.refresh
      .track(
        this.t('refresh.savingTicketData'),
        this.http
          .put<AppSettings>(
            this.apiUrl('/settings/ticket'),
            {
              storeName: this.settingsForm.storeName,
              phone: this.settingsForm.phone,
              street: this.settingsForm.street,
              neighborhood: this.settingsForm.neighborhood,
              city: this.settingsForm.city,
              postalCode: this.settingsForm.postalCode,
              contactEmail: this.settingsForm.contactEmail,
              instagramHandle: this.settingsForm.instagramHandle,
              socialNetwork: this.settingsForm.socialNetwork,
              logoUrl: this.settingsForm.logoUrl,
              thankYouMessage: this.settingsForm.thankYouMessage,
              ticketPrefix: this.settingsForm.ticketPrefix,
              ticketFooterNote: this.settingsForm.ticketFooterNote,
              ticketPaperSize: this.settingsForm.ticketPaperSize,
              showLogoOnTicket: this.settingsForm.showLogoOnTicket,
              showAddressOnTicket: this.settingsForm.showAddressOnTicket,
              showPhoneOnTicket: this.settingsForm.showPhoneOnTicket,
              showCustomerOnTicket: this.settingsForm.showCustomerOnTicket,
              showSavingsOnTicket: this.settingsForm.showSavingsOnTicket,
              showChangeOnTicket: this.settingsForm.showChangeOnTicket,
              autoOpenTicket: this.settingsForm.autoOpenTicket,
            },
            this.authOptions(),
          )
          .pipe(timeout({ first: SAVE_TIMEOUT_MS })),
      )
      .pipe(finalize(() => (this.isSavingTicketSettings = false)))
      .subscribe({
        next: (settings) => {
          this.applySettings(settings);
          this.settingsMessage = this.t('ok.ticketSettingsSaved');
          this.statusMessage = this.t('ok.ticketSettingsSaved');
        },
        error: () => {
          this.settingsMessage = this.t('err.ticketSettingsFailed');
          this.statusMessage = this.settingsMessage;
        },
      });
  }

  saveCredentials(): void {
    if (!this.credentialsForm.username.trim()) {
      this.credentialsMessage = this.t('err.usernameRequired');
      this.statusMessage = this.credentialsMessage;
      return;
    }
    if (!this.credentialsForm.currentPassword.trim()) {
      this.credentialsMessage = this.t('err.currentPasswordRequired');
      this.statusMessage = this.credentialsMessage;
      return;
    }

    this.isSavingCredentials = true;
    this.refresh
      .track(
        this.t('refresh.savingAccess'),
        this.http
          .put<AppSettings>(
            this.apiUrl('/settings/credentials'),
            this.credentialsForm,
            this.authOptions(),
          )
          .pipe(timeout({ first: SAVE_TIMEOUT_MS })),
      )
      .pipe(finalize(() => (this.isSavingCredentials = false)))
      .subscribe({
        next: (settings) => {
          this.applySettings(settings);
          this.credentialsForm.currentPassword = '';
          this.credentialsForm.newPassword = '';
          this.credentialsMessage = this.t('ok.credentialsSaved');
          this.statusMessage = this.t('ok.accessUpdated');
        },
        error: () => {
          this.credentialsMessage = this.t('err.credentialsFailed');
          this.statusMessage = this.credentialsMessage;
        },
      });
  }

  onLogoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      this.settingsForm.logoUrl = String(reader.result || '');
      this.logoFileName = file.name;
      this.settingsMessage = this.t('ok.logoLoaded');
    };
    reader.readAsDataURL(file);
  }

  downloadBackup(): void {
    this.runBackupExport(this.t('refresh.generatingExcel'), (backup) => {
      const payload = this.buildBackupWorkbook(backup);
      const blob = new Blob([payload], { type: 'application/vnd.ms-excel;charset=utf-8' });
      this.downloadBlob(blob, `boutique-os-respaldo-${this.todayDateString()}.xls`);
      this.settingsMessage = this.t('ok.backupExcel');
    });
  }

  downloadBackupCsv(): void {
    this.runBackupExport(this.t('refresh.generatingCsv'), (backup) => {
      const payload = this.buildBackupCsv(backup);
      const blob = new Blob([payload], { type: 'text/csv;charset=utf-8' });
      this.downloadBlob(blob, `boutique-os-respaldo-${this.todayDateString()}.csv`);
      this.settingsMessage = this.t('ok.backupCsv');
    });
  }

  downloadBackupPdf(): void {
    this.runBackupExport(this.t('refresh.generatingPdf'), async (backup) => {
      await this.openBackupPdf(backup);
      this.settingsMessage = this.t('ok.backupPdf');
    });
  }

  exportDailyReportPdf(): void {
    void this.openDailyReportPdf();
  }

  saveCashCount(): void {
    if (this.reportDayClosed) {
      this.statusMessage = this.t('warn.dayClosed');
      return;
    }
    this.isSavingCashCount = true;
    this.refresh
      .track(
        this.t('refresh.savingCashCount'),
        this.http.put<DailyCashCount>(
          this.apiUrl(`/reports/cash-count/today?date=${this.reportDate}`),
          {
            actualCash: this.actualCashInput,
            notes: this.cashCountNotes || null,
          },
        ),
      )
      .subscribe({
        next: (cashCount) => {
          this.applyDailyCashCount(cashCount);
          this.statusMessage = this.t('ok.cashCountSaved');
          this.isSavingCashCount = false;
        },
        error: () => {
          this.statusMessage = this.t('err.cashCountFailed');
          this.isSavingCashCount = false;
        },
      });
  }

  closeReportDay(): void {
    if (this.reportDayClosed) {
      this.statusMessage = this.t('warn.alreadyClosed');
      return;
    }
    this.isClosingReportDay = true;
    this.refresh
      .track(
        this.t('refresh.closingDay'),
        this.http.post<DailyCashCount>(
          this.apiUrl(`/reports/cash-count/today/close?date=${this.reportDate}`),
          {},
        ),
      )
      .subscribe({
        next: (cashCount) => {
          this.applyDailyCashCount(cashCount);
          this.statusMessage = this.t('ok.dayClosedSuccess');
          this.isClosingReportDay = false;
        },
        error: () => {
          this.statusMessage = this.t('err.closeDayFailed');
          this.isClosingReportDay = false;
        },
      });
  }

  reopenReportDay(): void {
    if (!this.reportDayClosed) {
      this.statusMessage = this.t('warn.alreadyOpen');
      return;
    }
    this.isReopeningReportDay = true;
    this.refresh
      .track(
        this.t('refresh.reopeningDay'),
        this.http.post<DailyCashCount>(
          this.apiUrl(`/reports/cash-count/today/reopen?date=${this.reportDate}`),
          {},
        ),
      )
      .subscribe({
        next: (cashCount) => {
          this.applyDailyCashCount(cashCount);
          this.statusMessage = this.t('ok.dayReopened');
          this.isReopeningReportDay = false;
        },
        error: () => {
          this.statusMessage = this.t('err.reopenDayFailed');
          this.isReopeningReportDay = false;
        },
      });
  }

  openLastTicketPdf(): void {
    if (!this.lastTicket) {
      this.statusMessage = this.t('warn.noRecentTicket');
      return;
    }
    void this.openTicketPdf(this.lastTicket);
    this.statusMessage = this.t('ok.ticketPdfOpened');
  }

  openSaleTicketPdf(sale: SaleRecord): void {
    void this.openTicketPdf(sale);
    this.statusMessage = this.t('ok.ticketPdfOpenedId', { id: sale.id });
  }

  formatMoney(value: number): string {
    return `$${value.toLocaleString('es-MX', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }

  formatDateTime(value: string): string {
    return new Date(value).toLocaleString('es-MX', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  paymentLabel(method: PaymentMethod): string {
    return this.t(`payment.${method}`);
  }

  saleStatusLabel(status: SaleStatus): string {
    return this.t(`saleStatus.${status}`);
  }

  customerWhatsappHref(phone: string): string {
    const normalized = phone.replace(/\D/g, '');
    return normalized ? `https://wa.me/${normalized}` : '#';
  }

  promotionTypeLabel(type: PromotionType): string {
    return this.t(`promoType.${type}`);
  }

  promotionValueLabel(promo: Promotion): string {
    return promo.type === 'PERCENT' ? `${promo.value}%` : this.formatMoney(promo.value);
  }

  promotionScopeLabel(promo: Promotion): string {
    if (promo.customerId == null) return this.t('promoScope.ANY');
    const customer = this.customers.find((item) => item.id === promo.customerId);
    return customer
      ? this.t('promoScope.ONLY', { name: customer.name })
      : this.t('promoScope.SPECIFIC');
  }

  promotionWindowLabel(promo: Promotion): string {
    return promo.endsAt
      ? this.t('promoWindow.RANGE', { from: promo.startsAt, to: promo.endsAt })
      : this.t('promoWindow.FROM', { from: promo.startsAt });
  }

  get ticketQrLabel(): string {
    return this.settingsForm.instagramHandle.trim() || this.settings.instagramHandle || '';
  }

  promotionStatus(promo: Promotion): 'INACTIVE' | 'EXPIRED' | 'SCHEDULED' | 'ACTIVE' {
    if (!promo.active) return 'INACTIVE';
    if (this.isPromotionExpired(promo)) return 'EXPIRED';
    if (this.isPromotionFuture(promo)) return 'SCHEDULED';
    return 'ACTIVE';
  }

  promotionStatusLabel(promo: Promotion): string {
    return this.t(`promoStatus.${this.promotionStatus(promo)}`);
  }

  customerInitials(customer: Customer | null): string {
    if (!customer?.name.trim()) return 'CL';
    const parts = customer.name.trim().split(/\s+/).slice(0, 2);
    return parts.map((part) => part.charAt(0).toUpperCase()).join('');
  }

  productStatusLabel(status: ProductStatus): string {
    return this.t(`productStatus.${status}`);
  }

  inventoryMovementLabel(type: InventoryMovementType): string {
    return this.t(`movement.${type}`);
  }

  private async openTicketPdf(sale: SaleRecord): Promise<void> {
    const { jsPDF } = await import('jspdf');
    const format =
      this.settings.ticketPaperSize === 'THERMAL_58'
        ? [58, 220]
        : this.settings.ticketPaperSize === 'HALF_LETTER'
          ? [140, 216]
          : [80, 220];
    const doc = new jsPDF({
      orientation: 'portrait',
      unit: 'mm',
      format,
    });

    const pageWidth = format[0];
    const pageHeight = format[1];
    const margin = this.settings.ticketPaperSize === 'HALF_LETTER' ? 10 : 6;
    const contentWidth = pageWidth - margin * 2;
    const ticketRef = this.ticketReference(sale);
    const savings = Math.max(sale.subtotal - sale.total, 0);
    let y = 10;

    const ensureSpace = (needed = 8) => {
      if (y + needed <= pageHeight - margin) return;
      doc.addPage();
      y = margin + 4;
    };

    const center = (text: string, size = 10, style: 'normal' | 'bold' = 'normal') => {
      if (!text.trim()) return;
      ensureSpace(size * 0.7 + 4);
      doc.setFont('helvetica', style);
      doc.setFontSize(size);
      const lines = doc.splitTextToSize(text, contentWidth) as string[];
      doc.text(lines, pageWidth / 2, y, { align: 'center' });
      y += lines.length * (size * 0.42 + 1.3) + 1;
    };

    const line = () => {
      ensureSpace(4);
      doc.setDrawColor(190);
      doc.line(margin, y, pageWidth - margin, y);
      y += 4;
    };

    const row = (label: string, value: string, bold = false) => {
      ensureSpace(6);
      doc.setFont('helvetica', bold ? 'bold' : 'normal');
      doc.setFontSize(8.5);
      doc.text(label, margin, y);
      const lines = doc.splitTextToSize(value, contentWidth * 0.5) as string[];
      doc.text(lines, pageWidth - margin, y, { align: 'right' });
      y += Math.max(lines.length, 1) * 4.5;
    };

    const wrap = (text: string) => doc.splitTextToSize(text || '', contentWidth) as string[];

    if (this.settings.showLogoOnTicket && this.settings.logoUrl.startsWith('data:image/')) {
      try {
        const logoWidth = this.settings.ticketPaperSize === 'HALF_LETTER' ? 32 : 24;
        doc.addImage(
          this.settings.logoUrl,
          'PNG',
          (pageWidth - logoWidth) / 2,
          y,
          logoWidth,
          16,
          undefined,
          'FAST',
        );
        y += 19;
      } catch {
        y += 1;
      }
    }

    center(this.settings.storeName || 'Boutique OS', 12, 'bold');
    if (this.settings.showAddressOnTicket && this.settings.address)
      center(this.settings.address, 7);
    if (this.settings.showAddressOnTicket && !this.settings.address && this.settings.street) {
      center(this.settings.street, 7);
    }
    if (this.settings.showAddressOnTicket && !this.settings.address && this.settings.neighborhood) {
      center(this.settings.neighborhood, 7);
    }
    if (
      this.settings.showAddressOnTicket &&
      !this.settings.address &&
      (this.settings.city || this.settings.postalCode)
    ) {
      center(
        `${this.settings.city}${this.settings.city && this.settings.postalCode ? ' CP ' : ''}${this.settings.postalCode}`,
        7,
      );
    }
    if (this.settings.showPhoneOnTicket && this.settings.phone) center(this.settings.phone, 7);
    if (this.settings.contactEmail) center(this.settings.contactEmail, 7);
    if (this.settings.instagramHandle) center(this.settings.instagramHandle, 7);
    center(this.t('ticket.title'), 9, 'bold');
    y += 1;
    row(this.t('ticket.folio'), ticketRef, true);
    row(this.t('ticket.date'), this.formatDateTime(sale.createdAt));
    if (sale.refundedAt) {
      row(this.t('ticket.refunded'), this.formatDateTime(sale.refundedAt));
    }
    if (this.settings.showCustomerOnTicket) {
      row(this.t('ticket.customer'), sale.customerName || this.t('pos.counter'));
    }
    row(this.t('ticket.payment'), this.paymentLabel(sale.paymentMethod));
    row(this.t('ticket.status'), this.saleStatusLabel(sale.status));
    line();

    for (const item of sale.items) {
      ensureSpace(10);
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(8.5);
      const nameLines = wrap(item.productName);
      for (const nameLine of nameLines) {
        doc.text(nameLine, margin, y);
        y += 4.5;
      }

      doc.setFont('helvetica', 'normal');
      doc.setFontSize(8);
      const detail =
        item.refundedQuantity > 0
          ? `${item.quantity} x ${this.formatMoney(item.unitPrice)} (${this.t('ticket.refundedQty', { n: item.refundedQuantity })})`
          : `${item.quantity} x ${this.formatMoney(item.unitPrice)}`;
      doc.text(detail, margin, y);
      doc.text(this.formatMoney(item.lineTotal), pageWidth - margin, y, { align: 'right' });
      y += 5;
    }

    line();
    row(this.t('ticket.subtotal'), this.formatMoney(sale.subtotal));
    if (this.settings.showSavingsOnTicket && savings > 0) {
      row(this.t('ticket.savings'), `-${this.formatMoney(savings)}`);
    }
    if (sale.refundedTotal > 0) {
      row(this.t('ticket.refundedTotal'), `-${this.formatMoney(sale.refundedTotal)}`);
    }
    row(this.t('ticket.total'), this.formatMoney(sale.total), true);
    if (sale.paymentMethod === 'CASH') {
      row(this.t('ticket.received'), this.formatMoney(sale.cashReceived || 0));
      if (this.settings.showChangeOnTicket) {
        row(this.t('ticket.change'), this.formatMoney(sale.changeDue || 0), true);
      }
    }
    row(this.t('ticket.pieces'), String(sale.items.reduce((sum, item) => sum + item.quantity, 0)));
    y += 3;
    line();
    const qrDataUrl = await this.generateTicketQrDataUrl();
    if (qrDataUrl && this.ticketQrLabel) {
      const qrSize = this.settings.ticketPaperSize === 'HALF_LETTER' ? 28 : 22;
      ensureSpace(qrSize + 16);
      try {
        doc.addImage(
          qrDataUrl,
          'PNG',
          (pageWidth - qrSize) / 2,
          y,
          qrSize,
          qrSize,
          undefined,
          'FAST',
        );
        y += qrSize + 4;
        center(this.ticketQrLabel, 7, 'bold');
      } catch {
        y += 2;
      }
      line();
    }
    center(this.settings.thankYouMessage || this.t('ticket.thankYou'), 8, 'bold');
    if (this.settings.ticketFooterNote) {
      center(this.settings.ticketFooterNote, 7);
    }

    const blobUrl = doc.output('bloburl');
    const printWindow = window.open(blobUrl, '_blank', 'noopener,noreferrer');

    if (!printWindow) {
      doc.save(this.ticketFilename(sale));
      this.statusMessage = this.t('warn.couldNotOpenPdfWindow');
    }
  }

  private ticketReference(sale: SaleRecord): string {
    const prefix = (this.settings.ticketPrefix || 'BOS').trim().toUpperCase();
    return `${prefix}-${String(sale.id).padStart(4, '0')}`;
  }

  private ticketFilename(sale: SaleRecord): string {
    return `ticket-${this.ticketReference(sale).toLowerCase()}.pdf`;
  }

  private async openDailyReportPdf(): Promise<void> {
    const { jsPDF } = await import('jspdf');
    const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });
    const margin = 14;
    const pageWidth = doc.internal.pageSize.getWidth();
    const pageHeight = doc.internal.pageSize.getHeight();
    const contentWidth = pageWidth - margin * 2;
    let y = 18;

    const ensureSpace = (needed = 10) => {
      if (y + needed <= pageHeight - 14) return;
      doc.addPage();
      y = 18;
    };

    const section = (title: string) => {
      ensureSpace(12);
      doc.setFillColor(239, 243, 248);
      doc.roundedRect(margin, y - 5, contentWidth, 8, 1.5, 1.5, 'F');
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(11);
      doc.text(title, margin + 3, y);
      y += 8;
    };

    const row = (label: string, value: string, bold = false) => {
      ensureSpace(5);
      doc.setFont('helvetica', bold ? 'bold' : 'normal');
      doc.setFontSize(9);
      doc.text(label, margin, y);
      doc.text(value, pageWidth - margin, y, { align: 'right' });
      y += 5;
    };

    const paragraph = (text: string, size = 8.5, style: 'normal' | 'bold' = 'normal') => {
      const lines = doc.splitTextToSize(text, contentWidth) as string[];
      ensureSpace(lines.length * 4 + 2);
      doc.setFont('helvetica', style);
      doc.setFontSize(size);
      doc.text(lines, margin, y);
      y += lines.length * 4 + 2;
    };

    doc.setFont('helvetica', 'bold');
    doc.setFontSize(16);
    doc.text(
      `${this.t('reportPdf.title')} · ${this.settings.storeName || 'Boutique OS'}`,
      margin,
      y,
    );
    y += 7;
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(9);
    doc.text(`${this.t('reportPdf.date')} ${this.reportDate}`, margin, y);
    doc.text(
      `${this.t('reportPdf.generated')} ${this.formatDateTime(new Date().toISOString())}`,
      pageWidth - margin,
      y,
      {
        align: 'right',
      },
    );
    y += 10;

    section(this.t('reportPdf.executive'));
    row(this.t('reportPdf.netSold'), this.formatMoney(this.todayTotal), true);
    row(this.t('reportPdf.netProfit'), this.formatMoney(this.todayProfit));
    row(this.t('reportPdf.ticketsCharged'), String(this.confirmedSalesToday.length));
    row(this.t('reportPdf.ticketsPending'), String(this.pendingSalesCount));
    row(this.t('reportPdf.expectedBox'), this.formatMoney(this.expectedBoxTotal));
    row(this.t('reportPdf.expectedCash'), this.formatMoney(this.cashExpected));
    row(this.t('reportPdf.actualCash'), this.formatMoney(this.actualCashInput || 0));
    row(this.t('reportPdf.difference'), this.formatMoney(this.cashDifference), true);

    section(this.t('reportPdf.comparison'));
    for (const item of this.reportComparisonItems) {
      paragraph(
        `${item.title}: ${this.t('reportPdf.today')} ${item.current} · ${this.t('reportPdf.yesterday')} ${item.previous} · ${item.detail}`,
      );
    }

    section(this.t('reportPdf.metrics'));
    row(this.t('reportPdf.averageTicket'), this.formatMoney(this.averageTicketToday));
    row(this.t('reportPdf.piecesSold'), String(this.piecesSoldToday));
    row(this.t('reportPdf.averageMargin'), `${this.averageMarginToday.toFixed(1)}%`);
    row(this.t('reportPdf.peakHour'), this.peakHourLabel);
    row(
      this.t('reportPdf.topProduct'),
      this.topSellingProductToday
        ? `${this.topSellingProductToday.name} · ${this.topSellingProductToday.qty} ${this.t('common.units')}`
        : this.t('reportPdf.noConfirmedSales'),
    );

    section(this.t('reportPdf.byMethod'));
    for (const item of this.paymentSummary) {
      paragraph(
        `${item.label}: ${this.formatMoney(item.total)} · ${this.t('summary.countSales', { n: item.count })} · ${this.t('reportPdf.averageShort', { value: this.formatMoney(item.average) })}${item.refunds ? ` · ${this.t('summary.refundsShort', { n: item.refunds })}` : ''}`,
      );
    }

    section(this.t('reportPdf.alerts'));
    for (const alert of this.reportAlerts) {
      paragraph(`${alert.title}: ${alert.detail}`, 8.5, alert.tone === 'risk' ? 'bold' : 'normal');
    }

    section(this.t('reportPdf.cashCut'));
    row(this.t('reportPdf.currentDifference'), this.formatMoney(this.cashDifference), true);
    row(
      this.t('reportPdf.dayStatus'),
      this.reportDayClosed ? this.t('reportPdf.closed') : this.t('reportPdf.open'),
    );
    row(
      this.t('reportPdf.closedAt'),
      this.reportClosedAt ? this.formatDateTime(this.reportClosedAt) : this.t('history.noClosing'),
    );
    row(
      this.t('reportPdf.lastSaved'),
      this.cashCountUpdatedAt
        ? this.formatDateTime(this.cashCountUpdatedAt)
        : this.t('reportPdf.notSaved'),
    );
    paragraph(`${this.t('reportPdf.notes')} ${this.cashCountNotes || this.t('reportPdf.noNotes')}`);

    if (this.topProductsToday.length) {
      section(this.t('reportPdf.topProducts'));
      for (const item of this.topProductsToday) {
        row(item.name, `${item.qty} ${this.t('common.units')}`);
      }
    }

    doc.save(`corte-diario-${this.reportDate}.pdf`);
    this.statusMessage = this.t('ok.reportPdfDownloaded');
  }

  async refreshTicketQrPreview(): Promise<void> {
    const seq = ++this.ticketQrRefreshSeq;
    const nextDataUrl = await this.generateTicketQrDataUrl();
    if (seq !== this.ticketQrRefreshSeq) {
      return;
    }
    this.ticketQrDataUrl = nextDataUrl;
  }

  get currentSocialNetwork(): string {
    return (
      this.settingsForm.socialNetwork ||
      this.settings.socialNetwork ||
      'INSTAGRAM'
    ).toUpperCase();
  }

  get ticketSocialPlaceholder(): string {
    switch (this.currentSocialNetwork) {
      case 'FACEBOOK':
        return 'tu-pagina';
      case 'TIKTOK':
        return '@tu-usuario';
      case 'WHATSAPP':
        return '81 0000 0000';
      case 'CUSTOM':
        return 'https://tuenlace.com';
      default:
        return '@tu-usuario';
    }
  }

  private async generateTicketQrDataUrl(): Promise<string> {
    const url = this.ticketSocialUrl(
      this.currentSocialNetwork,
      this.settingsForm.instagramHandle || this.settings.instagramHandle,
    );
    if (!url) return '';
    try {
      const module = await import('qrcode');
      const toDataURL = module.toDataURL || (module as any).default?.toDataURL;
      if (!toDataURL) return '';
      return await toDataURL(url, {
        errorCorrectionLevel: 'M',
        margin: 1,
        width: 240,
        color: {
          dark: '#111827',
          light: '#FFFFFF',
        },
      });
    } catch {
      return '';
    }
  }

  private ticketSocialUrl(network: string, value: string): string {
    const clean = (value || '').trim().replace(/^@+/, '').replace(/\s+/g, '');
    if (!clean) return '';
    switch (network) {
      case 'FACEBOOK':
        return `https://facebook.com/${clean}`;
      case 'TIKTOK':
        return `https://tiktok.com/@${clean}`;
      case 'WHATSAPP': {
        const digits = clean.replace(/\D+/g, '');
        return digits ? `https://wa.me/${digits}` : '';
      }
      case 'CUSTOM':
        return /^https?:\/\//i.test(clean) ? clean : `https://${clean}`;
      default:
        return `https://instagram.com/${clean}`;
    }
  }

  private resetCustomerForm(): void {
    this.editingCustomerId = null;
    this.newCustomerName = '';
    this.newCustomerPhone = '';
    this.newCustomerNotes = '';
  }

  private loadProducts(): void {
    this.http.get<Product[]>(this.apiUrl('/products')).subscribe({
      next: (products) => {
        this.products = products;
      },
      error: () => {
        this.statusMessage = this.t('err.couldNotLoadProducts');
      },
    });
  }

  private buildBackupWorkbook(backup: Record<string, unknown>): string {
    const sections = this.backupSections(backup);
    const generatedAt = this.escapeHtml(String(backup['generatedAt'] || new Date().toISOString()));
    const body = sections.map(([title, data]) => this.backupSection(title, data)).join('');

    return `<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <style>
    body { font-family: Arial, sans-serif; color: #1f2933; }
    h1 { font-size: 20px; margin: 0 0 4px; }
    h2 { font-size: 15px; margin: 24px 0 8px; background: #e8edf3; padding: 8px; border: 1px solid #c8d1db; }
    .meta { color: #66717f; margin-bottom: 16px; }
    table { border-collapse: collapse; width: 100%; margin-bottom: 10px; }
    th { background: #2f5f98; color: #ffffff; font-weight: bold; }
    th, td { border: 1px solid #c8d1db; padding: 6px; font-size: 12px; vertical-align: top; }
    td { mso-number-format:"\\@"; }
  </style>
</head>
<body>
  <h1>Respaldo Boutique OS</h1>
  <div class="meta">Generado: ${generatedAt}</div>
  ${body}
</body>
</html>`;
  }

  private backupSection(title: string, rows: unknown[]): string {
    if (!rows.length) {
      return `<h2>${this.escapeHtml(title)}</h2><p>Sin datos</p>`;
    }

    const normalized = rows.map((row) => this.flattenBackupRow(row));
    const headers = [...new Set(normalized.flatMap((row) => Object.keys(row)))];
    const head = headers
      .map((header) => `<th>${this.escapeHtml(this.humanizeBackupHeader(header))}</th>`)
      .join('');
    const body = normalized
      .map(
        (row) =>
          `<tr>${headers.map((header) => `<td>${this.escapeHtml(row[header] ?? '')}</td>`).join('')}</tr>`,
      )
      .join('');
    return `<h2>${this.escapeHtml(title)}</h2><table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table>`;
  }

  private flattenBackupRow(row: unknown): Record<string, string> {
    if (!row || typeof row !== 'object') {
      return { valor: String(row ?? '') };
    }

    const output: Record<string, string> = {};
    for (const [key, value] of Object.entries(row as Record<string, unknown>)) {
      if (Array.isArray(value)) {
        output[key] = value.map((item) => JSON.stringify(item)).join(' | ');
      } else if (value && typeof value === 'object') {
        output[key] = JSON.stringify(value);
      } else {
        output[key] = String(value ?? '');
      }
    }
    return output;
  }

  private humanizeBackupHeader(value: string): string {
    return value
      .replace(/([A-Z])/g, ' $1')
      .replace(/_/g, ' ')
      .trim()
      .replace(/^./, (letter) => letter.toUpperCase());
  }

  private backupSections(backup: Record<string, unknown>): Array<[string, unknown[]]> {
    return [
      ['Configuracion', [backup['settings']]],
      ['Productos', Array.isArray(backup['products']) ? backup['products'] : []],
      ['Categorias', Array.isArray(backup['productCategories']) ? backup['productCategories'] : []],
      ['Clientes', Array.isArray(backup['customers']) ? backup['customers'] : []],
      ['Ventas', Array.isArray(backup['sales']) ? backup['sales'] : []],
      ['Devoluciones', Array.isArray(backup['saleRefunds']) ? backup['saleRefunds'] : []],
      ['Compras', Array.isArray(backup['purchases']) ? backup['purchases'] : []],
      [
        'Movimientos de inventario',
        Array.isArray(backup['inventoryMovements']) ? backup['inventoryMovements'] : [],
      ],
      ['Cortes de caja', Array.isArray(backup['dailyCashCounts']) ? backup['dailyCashCounts'] : []],
    ];
  }

  private buildBackupCsv(backup: Record<string, unknown>): string {
    const rows = this.normalizedBackupRows(backup);
    const headers = [
      'seccion',
      ...new Set(rows.flatMap((row) => Object.keys(row).filter((key) => key !== 'seccion'))),
    ];
    const csvRows = [
      headers.join(','),
      ...rows.map((row) => headers.map((header) => this.csvCell(row[header] ?? '')).join(',')),
    ];
    return csvRows.join('\n');
  }

  private normalizedBackupRows(backup: Record<string, unknown>): Array<Record<string, string>> {
    return this.backupSections(backup).flatMap(([section, rows]) => {
      if (!rows.length) {
        return [{ seccion: section, estado: 'Sin datos' }];
      }

      return rows.map((row) => ({
        seccion: section,
        ...this.flattenBackupRow(row),
      }));
    });
  }

  private csvCell(value: string): string {
    const safe = value.replace(/"/g, '""').replace(/\r?\n/g, ' ');
    return `"${safe}"`;
  }

  private downloadBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
  }

  private runBackupExport(
    loadingMessage: string,
    exporter: (backup: Record<string, unknown>) => void | Promise<void>,
  ): void {
    this.refresh
      .track(
        loadingMessage,
        this.http.get<Record<string, unknown>>(this.apiUrl('/backup'), this.authOptions()),
      )
      .subscribe({
        next: (backup) => {
          Promise.resolve(exporter(backup)).catch(() => {
            this.settingsMessage = this.t('err.backupFailed');
            this.statusMessage = this.settingsMessage;
          });
        },
        error: () => {
          this.settingsMessage = this.t('err.backupFailed');
          this.statusMessage = this.settingsMessage;
        },
      });
  }

  private async openBackupPdf(backup: Record<string, unknown>): Promise<void> {
    const { jsPDF } = await import('jspdf');
    const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });
    const margin = 14;
    const pageWidth = doc.internal.pageSize.getWidth();
    const pageHeight = doc.internal.pageSize.getHeight();
    const maxWidth = pageWidth - margin * 2;
    let y = 18;

    const ensureSpace = (needed = 10) => {
      if (y + needed <= pageHeight - 14) return;
      doc.addPage();
      y = 18;
    };

    doc.setFont('helvetica', 'bold');
    doc.setFontSize(16);
    doc.text(this.t('backupPdf.title'), margin, y);
    y += 7;
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(9);
    doc.text(
      `${this.t('reportPdf.generated')} ${this.formatDateTime(String(backup['generatedAt'] || new Date().toISOString()))}`,
      margin,
      y,
    );
    y += 8;

    for (const [section, rows] of this.backupSections(backup)) {
      ensureSpace(14);
      doc.setDrawColor(210, 218, 228);
      doc.setFillColor(232, 237, 243);
      doc.roundedRect(margin, y - 5, maxWidth, 8, 1.5, 1.5, 'FD');
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(11);
      doc.text(`${this.backupSectionLabel(section)} (${rows.length})`, margin + 3, y);
      y += 8;

      if (!rows.length) {
        doc.setFont('helvetica', 'normal');
        doc.setFontSize(8.5);
        doc.text(this.t('backupPdf.noData'), margin, y);
        y += 6;
        continue;
      }

      for (const row of rows.slice(0, 18)) {
        const flattened = this.flattenBackupRow(row);
        const summary = Object.entries(flattened)
          .slice(0, 5)
          .map(([key, value]) => `${this.humanizeBackupHeader(key)}: ${value}`)
          .join(' | ');
        const lines = doc.splitTextToSize(
          summary || this.t('backupPdf.noData'),
          maxWidth,
        ) as string[];
        ensureSpace(lines.length * 4 + 2);
        doc.setFont('helvetica', 'normal');
        doc.setFontSize(8.2);
        doc.text(lines, margin, y);
        y += lines.length * 4 + 2;
      }

      if (rows.length > 18) {
        ensureSpace(6);
        doc.setFont('helvetica', 'italic');
        doc.setFontSize(8);
        doc.text(this.t('backupPdf.moreRecords', { n: rows.length - 18 }), margin, y);
        y += 6;
      }

      y += 3;
    }

    doc.save(`boutique-os-respaldo-${this.todayDateString()}.pdf`);
  }

  private backupSectionLabel(section: string): string {
    const keys: Record<string, string> = {
      Configuracion: 'backup.configuration',
      Productos: 'backup.products',
      Categorias: 'backup.categories',
      Clientes: 'backup.customers',
      Ventas: 'backup.sales',
      Devoluciones: 'backup.refunds',
      Compras: 'backup.purchases',
      'Movimientos de inventario': 'backup.inventoryMovements',
      'Cortes de caja': 'backup.cashCounts',
    };
    const key = keys[section];
    return key ? this.t(key) : section;
  }

  private escapeHtml(value: string): string {
    return value
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  private loadSettings(): void {
    this.http.get<AppSettings>(this.apiUrl('/settings'), this.authOptions()).subscribe({
      next: (settings) => {
        this.applySettings(settings);
      },
      error: () => {
        this.settingsMessage = this.t('err.couldNotLoadSettings');
      },
    });
  }

  private applySettings(settings: AppSettings): void {
    this.settings = settings;
    this.settingsForm = {
      storeName: settings.storeName || 'Boutique OS',
      phone: settings.phone || '',
      street: settings.street || '',
      neighborhood: settings.neighborhood || '',
      city: settings.city || '',
      postalCode: settings.postalCode || '',
      contactEmail: settings.contactEmail || '',
      instagramHandle: settings.instagramHandle || '',
      socialNetwork: settings.socialNetwork || 'INSTAGRAM',
      logoUrl: settings.logoUrl || '',
      address: settings.address || '',
      thankYouMessage: settings.thankYouMessage || 'Gracias por tu compra',
      ticketPrefix: settings.ticketPrefix || 'BOS',
      ticketFooterNote: settings.ticketFooterNote || '',
      ticketPaperSize: settings.ticketPaperSize || 'THERMAL_80',
      showLogoOnTicket: settings.showLogoOnTicket ?? true,
      showAddressOnTicket: settings.showAddressOnTicket ?? true,
      showPhoneOnTicket: settings.showPhoneOnTicket ?? true,
      showCustomerOnTicket: settings.showCustomerOnTicket ?? true,
      showSavingsOnTicket: settings.showSavingsOnTicket ?? true,
      showChangeOnTicket: settings.showChangeOnTicket ?? true,
      autoOpenTicket: settings.autoOpenTicket ?? true,
    };
    this.credentialsForm = {
      username: settings.username || 'admin',
      currentPassword: '',
      newPassword: '',
    };
    void this.refreshTicketQrPreview();
  }

  private loadProductCategories(): void {
    this.http.get<ProductCategory[]>(this.apiUrl('/product-categories')).subscribe({
      next: (categories) => {
        this.productCategories = categories;
      },
      error: () => {
        this.statusMessage = this.t('err.couldNotLoadCategories');
      },
    });
  }

  private loadSalesToday(): void {
    this.http.get<SaleRecord[]>(this.apiUrl(`/sales/today?date=${this.reportDate}`)).subscribe({
      next: (sales) => {
        this.salesToday = sales;
      },
      error: () => {
        this.statusMessage = this.t('err.couldNotLoadReport');
      },
    });
  }

  private loadSalesYesterday(): void {
    const previousDate = this.previousDateString(this.reportDate);
    this.http.get<SaleRecord[]>(this.apiUrl(`/sales/today?date=${previousDate}`)).subscribe({
      next: (sales) => {
        this.salesYesterday = sales;
      },
      error: () => {
        this.salesYesterday = [];
      },
    });
  }

  private loadCustomers(): void {
    this.http.get<Customer[]>(this.apiUrl('/customers')).subscribe({
      next: (customers) => {
        this.customers = customers;
        if (
          this.selectedCustomerId &&
          !customers.find((customer) => customer.id === this.selectedCustomerId)
        ) {
          this.selectedCustomerId = null;
        }
      },
      error: () => {
        this.statusMessage = this.t('err.couldNotLoadCustomers');
      },
    });
  }

  private loadPendingSales(): void {
    this.http.get<SaleRecord[]>(this.apiUrl('/sales/pending')).subscribe({
      next: (sales) => {
        this.pendingSales = sales;
      },
    });
  }

  private loadAllSales(): void {
    this.http.get<SaleRecord[]>(this.apiUrl(`/sales?date=${this.reportDate}`)).subscribe({
      next: (sales) => {
        this.allSales = sales;
      },
    });
  }

  private loadRefundsToday(): void {
    this.http
      .get<SaleRefundRecord[]>(this.apiUrl(`/sales/refunds/today?date=${this.reportDate}`))
      .subscribe({
        next: (refunds) => {
          this.refundsToday = refunds;
        },
        error: () => {
          this.refundsToday = [];
        },
      });
  }

  private loadRefundsYesterday(): void {
    const previousDate = this.previousDateString(this.reportDate);
    this.http
      .get<SaleRefundRecord[]>(this.apiUrl(`/sales/refunds/today?date=${previousDate}`))
      .subscribe({
        next: (refunds) => {
          this.refundsYesterday = refunds;
        },
        error: () => {
          this.refundsYesterday = [];
        },
      });
  }

  private loadCashCount(): void {
    this.http
      .get<DailyCashCount>(this.apiUrl(`/reports/cash-count/today?date=${this.reportDate}`))
      .subscribe({
        next: (cashCount) => {
          this.applyDailyCashCount(cashCount);
        },
        error: () => {
          this.actualCashInput = 0;
          this.cashCountNotes = '';
          this.cashCountUpdatedAt = null;
          this.reportDayClosed = false;
          this.reportClosedAt = null;
        },
      });
  }

  private loadReportHistory(): void {
    this.http.get<DailyCashCount[]>(this.apiUrl('/reports/cash-count/history')).subscribe({
      next: (history) => {
        this.reportHistory = history;
      },
      error: () => {
        this.reportHistory = [];
      },
    });
  }

  private refreshReportData(): void {
    this.loadSalesToday();
    this.loadSalesYesterday();
    this.loadAllSales();
    this.loadRefundsToday();
    this.loadRefundsYesterday();
    this.loadCashCount();
    this.loadReportHistory();
    this.loadReportInventoryMovements();
    this.loadYesterdayReportInventoryMovements();
  }

  private loadInventoryMovements(): void {
    this.http
      .get<InventoryMovement[]>(this.apiUrl(`/inventory/movements?date=${this.inventoryDate}`))
      .subscribe({
        next: (movements) => {
          this.inventoryMovements = movements;
        },
      });
  }

  private loadReportInventoryMovements(): void {
    this.http
      .get<InventoryMovement[]>(this.apiUrl(`/inventory/movements?date=${this.reportDate}`))
      .subscribe({
        next: (movements) => {
          this.reportInventoryMovements = movements;
        },
        error: () => {
          this.reportInventoryMovements = [];
        },
      });
  }

  private loadYesterdayReportInventoryMovements(): void {
    const previousDate = this.previousDateString(this.reportDate);
    this.http
      .get<InventoryMovement[]>(this.apiUrl(`/inventory/movements?date=${previousDate}`))
      .subscribe({
        next: (movements) => {
          this.reportInventoryMovementsYesterday = movements;
        },
        error: () => {
          this.reportInventoryMovementsYesterday = [];
        },
      });
  }

  private loadPurchases(): void {
    this.http
      .get<PurchaseRecord[]>(this.apiUrl(`/purchases?date=${this.inventoryDate}`))
      .subscribe({
        next: (purchases) => {
          this.recentPurchases = purchases;
        },
      });
  }

  private refreshInventoryData(): void {
    this.loadInventoryMovements();
    this.loadPurchases();
  }

  private adjustInventory(
    productId: number,
    quantityDelta: number,
    note: string,
    message: string,
  ): void {
    this.refresh
      .track(
        this.t('refresh.updatingInventory'),
        this.http.post<Product>(this.apiUrl('/inventory/adjustments'), {
          productId,
          quantityDelta,
          note,
        }),
      )
      .subscribe({
        next: () => {
          this.statusMessage = message;
          this.loadProducts();
          this.loadInventoryMovements();
        },
        error: () => {
          this.statusMessage = this.t('err.couldNotUpdateInventory');
        },
      });
  }

  private resetPromoForm(): void {
    this.editingPromoId = null;
    this.promoForm = {
      name: '',
      code: '',
      type: 'PERCENT',
      value: 10,
      minSubtotal: 0,
      customerId: null,
      startsAt: this.todayDateString(),
      endsAt: '',
      active: true,
      notes: '',
    };
  }

  private persistPromotions(): void {
    if (typeof window === 'undefined') return;
    window.localStorage.setItem(PROMOS_STORAGE_KEY, JSON.stringify(this.promotions));
  }

  private loadPromotionsFromStorage(): void {
    if (typeof window === 'undefined') return;
    const raw = window.localStorage.getItem(PROMOS_STORAGE_KEY);
    if (!raw) return;
    try {
      const parsed = JSON.parse(raw);
      if (!Array.isArray(parsed)) return;
      this.promotions = parsed
        .filter((item) => item && typeof item === 'object')
        .map((item) => ({
          id: String(item.id ?? this.generatePromoId()),
          name: String(item.name ?? ''),
          code: this.normalizePromoCode(String(item.code ?? '')),
          type: (item.type === 'FIXED' ? 'FIXED' : 'PERCENT') as PromotionType,
          value: Math.max(Number(item.value) || 0, 0),
          minSubtotal: Math.max(Number(item.minSubtotal) || 0, 0),
          customerId:
            item.customerId == null || Number.isNaN(Number(item.customerId))
              ? null
              : Number(item.customerId),
          startsAt: String(item.startsAt ?? this.todayDateString()),
          endsAt: item.endsAt ? String(item.endsAt) : null,
          active: Boolean(item.active),
          notes: String(item.notes ?? ''),
          createdAt: String(item.createdAt ?? new Date().toISOString()),
        }))
        .filter((promo) => promo.name && promo.code);
    } catch {
      this.promotions = [];
    }
  }

  private syncSelectedPromo(): void {
    if (this.selectedPromoId && !this.activeCartPromo) {
      this.selectedPromoId = null;
    }
  }

  private shouldQueueOffline(error: unknown): boolean {
    if (typeof navigator !== 'undefined' && navigator.onLine === false) {
      return true;
    }
    return error instanceof HttpErrorResponse && error.status === 0;
  }

  private queueOfflineSale(payload: OfflineSaleEntry['payload']): void {
    const entries = this.loadOfflineSales();
    entries.push({
      id: `offline-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      payload,
      createdAt: new Date().toISOString(),
    });
    this.saveOfflineSales(entries);
  }

  flushOfflineSales(): void {
    if (
      typeof navigator === 'undefined' ||
      navigator.onLine === false ||
      !this.loggedIn ||
      !this.sessionToken
    ) {
      return;
    }
    const entries = this.loadOfflineSales();
    if (!entries.length) {
      return;
    }

    const remaining = entries.slice();
    let cursor = 0;
    const attemptNext = () => {
      if (cursor >= remaining.length) {
        this.saveOfflineSales([]);
        if (remaining.length) {
          const total = remaining.length;
          this.statusMessage = this.t(
            total === 1 ? 'ok.offlineSyncedOne' : 'ok.offlineSyncedMany',
            { n: total },
          );
          this.loadProducts();
          this.loadSalesToday();
          this.loadPendingSales();
          this.refreshReportData();
        }
        return;
      }
      const entry = remaining[cursor];
      this.http.post<SaleRecord>(this.apiUrl('/sales'), entry.payload).subscribe({
        next: () => {
          cursor += 1;
          attemptNext();
        },
        error: () => {
          this.saveOfflineSales(remaining.slice(cursor));
        },
      });
    };
    attemptNext();
  }

  private loadOfflineSales(): OfflineSaleEntry[] {
    if (typeof window === 'undefined') {
      return [];
    }
    try {
      const raw = window.localStorage.getItem(OFFLINE_QUEUE_KEY);
      if (!raw) {
        return [];
      }
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  private saveOfflineSales(entries: OfflineSaleEntry[]): void {
    if (typeof window === 'undefined') {
      return;
    }
    try {
      window.localStorage.setItem(OFFLINE_QUEUE_KEY, JSON.stringify(entries));
    } catch {
      // Storage no disponible o lleno: se mantiene la venta en el carrito.
    }
  }

  private normalizePromoCode(value: string): string {
    return value
      .trim()
      .toUpperCase()
      .replace(/\s+/g, '-')
      .replace(/[^A-Z0-9_-]/g, '');
  }

  private generatePromoId(): string {
    return `promo-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  }

  private promotionDiscountAmount(promo: Promotion, baseAmount: number): number {
    if (baseAmount <= 0) return 0;
    const raw = promo.type === 'PERCENT' ? (baseAmount * promo.value) / 100 : promo.value;
    return Math.min(Math.max(raw, 0), baseAmount);
  }

  private isPromotionApplicable(promo: Promotion): boolean {
    if (!promo.active || this.cartSubtotal <= 0) return false;
    if (promo.customerId != null && promo.customerId !== this.selectedCustomerId) return false;
    if (this.isPromotionFuture(promo) || this.isPromotionExpired(promo)) return false;
    return this.cartSubtotal >= promo.minSubtotal;
  }

  private isPromotionFuture(promo: Promotion): boolean {
    return Boolean(promo.startsAt) && promo.startsAt > this.todayDateString();
  }

  private isPromotionExpired(promo: Promotion): boolean {
    return Boolean(promo.endsAt) && (promo.endsAt ?? '') < this.todayDateString();
  }

  private viewLabel(view: ViewId): string {
    return this.t(`nav.${view}`);
  }

  private showAlert(message: string, type?: AlertType): void {
    if (!message || message === this.t('ok.ready')) return;
    this.alertMessage = message;
    this.alertType = type ?? this.inferAlertType(message);
    if (this.alertTimer) {
      clearTimeout(this.alertTimer);
    }
    this.alertTimer = setTimeout(() => {
      this.alertMessage = '';
      this.alertTimer = null;
    }, 3200);
  }

  private clearOnboardingQuery(): void {
    if (typeof window === 'undefined') {
      return;
    }
    const url = new URL(window.location.href);
    url.searchParams.delete('session_id');
    url.searchParams.delete('checkout_session_id');
    window.history.replaceState({}, '', url.toString());
  }

  private clearPasswordResetQuery(): void {
    if (typeof window === 'undefined') {
      return;
    }
    const url = new URL(window.location.href);
    url.searchParams.delete('resetToken');
    window.history.replaceState({}, '', url.toString());
  }

  private describeOnboardingError(error: unknown, fallback: string): string {
    if (error && typeof error === 'object' && 'name' in error && error.name === 'TimeoutError') {
      return this.t('err.onboardingTimeout');
    }
    if (error instanceof HttpErrorResponse) {
      if (error.status === 400) return this.t('err.onboardingPaymentInvalid');
      if (error.status === 404) return this.t('err.onboardingLinkInvalid');
      if (error.status === 409) return this.t('err.onboardingAlreadyUsed');
      if (error.status === 410) return this.t('err.onboardingLinkExpired');
      if (error.status === 503) return this.t('err.onboardingStripeNotConfigured');
    }
    return fallback;
  }

  private describePasswordResetError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 400) return this.t('err.resetPassFormat');
      if (error.status === 404) return this.t('err.resetLinkInvalid');
      if (error.status === 410) return this.t('err.resetLinkExpired');
      if (error.status === 429) return this.t('err.tooManyAttempts');
    }
    if (error && typeof error === 'object' && 'name' in error && error.name === 'TimeoutError') {
      return this.t('err.operationTimeout');
    }
    return this.t('err.resetGeneral');
  }

  private inferAlertType(message: string): AlertType {
    const text = message.toLowerCase();
    if (
      text.includes('no se') ||
      text.includes('no pude') ||
      text.includes('incorrect') ||
      text.includes('sin stock') ||
      text.includes('necesita') ||
      text.includes('obligatorio') ||
      text.includes('revisa') ||
      text.includes('no se pudo') ||
      text.includes('not have') ||
      text.includes('no stock') ||
      text.includes('required') ||
      text.includes('failed') ||
      text.includes('could not') ||
      text.includes('check ')
    ) {
      return 'error';
    }
    if (
      text.includes('pendiente') ||
      text.includes('selecciona') ||
      text.includes('actualizado') ||
      text.includes('pending') ||
      text.includes('select') ||
      text.includes('updated')
    ) {
      return 'warning';
    }
    if (
      text.includes('guardado') ||
      text.includes('agregado') ||
      text.includes('registrada') ||
      text.includes('confirmado') ||
      text.includes('descargado') ||
      text.includes('cobrada') ||
      text.includes('saved') ||
      text.includes('added') ||
      text.includes('registered') ||
      text.includes('confirmed') ||
      text.includes('downloaded') ||
      text.includes('charged')
    ) {
      return 'success';
    }
    return 'info';
  }

  private quantityInCart(productId: number): number {
    return this.cart.find((item) => item.productId === productId)?.qty ?? 0;
  }

  private sameCategory(left: string | null | undefined, right: string | null | undefined): boolean {
    return (left || '').trim().toLowerCase() === (right || '').trim().toLowerCase();
  }

  private findCategoryPreset(name: string | null | undefined): CategoryPreset | null {
    return this.categoryPresets.find((preset) => this.sameCategory(preset.name, name)) ?? null;
  }

  private inferCategoryPresetName(category: ProductCategory): string {
    const byName = this.findCategoryPreset(category.name);
    if (byName) return byName.name;
    const bySizeLabel = this.categoryPresets.find((preset) =>
      this.sameCategory(preset.sizeLabel, category.sizeLabel),
    );
    return bySizeLabel?.name || this.categoryPresets[0].name;
  }

  private isToday(value: string | null): boolean {
    if (!value) return false;
    const target = new Date(value);
    const today = new Date();
    return target.toDateString() === today.toDateString();
  }

  private resetProductForm(): void {
    this.editingProductId = null;
    this.productForm = {
      name: '',
      category: '',
      size: '',
      color: '',
      sku: '',
      imageUrl: '',
      costPrice: 0,
      salePrice: 0,
      stock: 0,
      status: 'ACTIVE',
    };
    this.productImageFileName = '';
  }

  private resetCategoryForm(): void {
    this.editingCategoryId = null;
    this.categoryForm = {
      presetName: this.categoryPresets[0].name,
      name: '',
      description: '',
      active: true,
    };
  }

  private todayDateString(): string {
    const now = new Date();
    const offset = now.getTimezoneOffset();
    return new Date(now.getTime() - offset * 60000).toISOString().slice(0, 10);
  }

  private previousDateString(value: string): string {
    const base = new Date(`${value}T00:00:00`);
    base.setDate(base.getDate() - 1);
    return this.toDateInputValue(base);
  }

  private toDateInputValue(date: Date): string {
    const offset = date.getTimezoneOffset();
    return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 10);
  }

  private applyDailyCashCount(cashCount: DailyCashCount): void {
    this.actualCashInput = cashCount.actualCash;
    this.cashCountNotes = cashCount.notes || '';
    this.cashCountUpdatedAt = cashCount.updatedAt;
    this.reportDayClosed = cashCount.closed;
    this.reportClosedAt = cashCount.closedAt;
  }

  private saleMatchesIncidentFilter(sale: SaleRecord): boolean {
    if (this.reportIncidentFilter === 'PENDING') {
      return sale.status === 'PENDING';
    }
    if (this.reportIncidentFilter === 'CANCELLED') {
      return sale.status === 'CANCELLED' || sale.status === 'REFUNDED';
    }
    return true;
  }

  private deltaTone(
    current: number,
    previous: number,
    lowerIsBetter = false,
  ): 'good' | 'warn' | 'risk' {
    if (current === previous) return 'warn';
    const improved = lowerIsBetter ? current < previous : current > previous;
    return improved ? 'good' : 'risk';
  }

  private describeMoneyDelta(current: number, previous: number, lowerIsBetter = false): string {
    const diff = current - previous;
    if (Math.abs(diff) < 0.01) {
      return this.t('comparison.noChange');
    }
    const direction = this.describeDeltaDirection(diff, lowerIsBetter);
    return `${diff > 0 ? '+' : ''}${this.formatMoney(diff)} ${direction}`;
  }

  private describeCountDelta(
    current: number,
    previous: number,
    label: string,
    lowerIsBetter = false,
  ): string {
    const diff = current - previous;
    if (diff === 0) {
      return this.t('comparison.noChangeIn', { label });
    }
    const direction = this.describeDeltaDirection(diff, lowerIsBetter);
    return `${diff > 0 ? '+' : ''}${diff} ${label}(s) ${direction}`;
  }

  private describeDeltaDirection(diff: number, lowerIsBetter: boolean): string {
    const goingUp = diff > 0;
    const better = lowerIsBetter ? !goingUp : goingUp;
    if (better) return this.t('comparison.lessThan');
    return this.t('comparison.moreThan');
  }

  private apiUrl(path: string): string {
    return `${this.apiBase}${path}`;
  }

  private authOptions(): { headers: HttpHeaders } {
    return {
      headers: new HttpHeaders({
        'X-Boutique-Session': this.sessionToken,
      }),
    };
  }

  private clearSessionState(clearCredentials: boolean): void {
    this.loggedIn = false;
    this.sessionToken = '';
    if (clearCredentials) {
      this.loginUser = '';
      this.loginPass = '';
      this.recoveryUser = '';
    }
    this.loginError = '';
    this.clearRecoveryState();
    this.cart = [];
    this.selectedCustomerId = null;
    this.checkoutDiscount = 0;
    this.cashReceived = 0;
    this.lastTicket = null;
  }

  private clearRecoveryState(): void {
    this.recoveryOpen = false;
    this.recoveryMode = 'request';
    this.recoveryToken = '';
    this.recoveryMaskedEmail = '';
    this.recoveryError = '';
    this.recoveryInfo = '';
    this.recoveryPass = '';
    this.recoveryConfirmPass = '';
    this.recoveryLoading = false;
    this.recoveryTokenChecking = false;
  }

  private resolveApiBase(): string {
    if (typeof window === 'undefined') {
      return 'http://localhost:8080/api';
    }
    const override = (window as Window & { __BOUTIQUE_API_URL__?: string }).__BOUTIQUE_API_URL__;
    if (override) {
      return override.replace(/\/$/, '');
    }
    if (window.location.port === '4200') {
      return 'http://localhost:8080/api';
    }
    return '/api';
  }
}
