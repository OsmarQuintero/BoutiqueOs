import { HttpClient, HttpHeaders } from '@angular/common/http';
import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, NgZone, signal } from '@angular/core';
import { finalize, timeout } from 'rxjs';
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

const LOGIN_TIMEOUT_MS = 8000;
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

@Injectable({ providedIn: 'root' })
export class StoreService {
  readonly apiBase = this.resolveApiBase();

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
  private _statusMessage = 'Listo para vender';
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

  readonly paymentMethods = [
    { label: 'Efectivo', value: 'CASH' as PaymentMethod },
    { label: 'Transferencia', value: 'TRANSFER' as PaymentMethod },
    { label: 'Tarjeta', value: 'CARD' as PaymentMethod },
  ];

  readonly productStatuses = [
    { label: 'Activo', value: 'ACTIVE' as ProductStatus },
    { label: 'Agotado', value: 'OUT_OF_STOCK' as ProductStatus },
    { label: 'Archivado', value: 'ARCHIVED' as ProductStatus },
  ];

  readonly promotionTypes = [
    { label: 'Porcentaje', value: 'PERCENT' as PromotionType },
    { label: 'Monto fijo', value: 'FIXED' as PromotionType },
  ];

  readonly ticketPaperSizes = [
    { label: 'Termica 58 mm', value: 'THERMAL_58' as TicketPaperSize },
    { label: 'Termica 80 mm', value: 'THERMAL_80' as TicketPaperSize },
    { label: 'Media carta', value: 'HALF_LETTER' as TicketPaperSize },
  ];

  readonly navItems = [
    { id: 'pos' as ViewId, label: 'Punto de venta', icon: '🛒' },
    { id: 'catalog' as ViewId, label: 'Catalogos', icon: '🏷️' },
    { id: 'categories' as ViewId, label: 'Categorias', icon: '🗂️' },
    { id: 'inventory' as ViewId, label: 'Inventario', icon: '📋' },
    { id: 'customers' as ViewId, label: 'Clientes', icon: '👥' },
    { id: 'promos' as ViewId, label: 'Promos', icon: '🎯' },
    { id: 'reports' as ViewId, label: 'Corte diario', icon: '📊' },
    { id: 'settings' as ViewId, label: 'Configuracion', icon: '⚙️' },
  ];

  readonly reportPanels = [
    { id: 'summary' as ReportPanel, label: 'Resumen' },
    { id: 'sales' as ReportPanel, label: 'Ventas' },
    { id: 'tickets' as ReportPanel, label: 'Tickets' },
    { id: 'refunds' as ReportPanel, label: 'Devoluciones' },
    { id: 'movements' as ReportPanel, label: 'Movimientos' },
    { id: 'history' as ReportPanel, label: 'Historial' },
  ];

  readonly inventoryPanels = [
    { id: 'summary' as InventoryPanel, label: 'Resumen' },
    { id: 'purchases' as InventoryPanel, label: 'Compras' },
  ];

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
  ) {
    this.loadPromotionsFromStorage();
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
    this.loginError = 'Tu sesion expiro. Vuelve a entrar.';
    this.alertType = 'warning';
    this.showAlert('La sesion expiro. Vuelve a iniciar sesion.');
  }

  completeOnboarding(): void {
    const storeName = this.onboardingForm.storeName.trim();
    const email = this.onboardingForm.email.trim().toLowerCase();
    const password = this.onboardingForm.password.trim();
    const confirmPassword = this.onboardingForm.confirmPassword.trim();

    if (!this.onboardingToken) {
      this.onboardingError = 'La sesion de activacion no es valida.';
      return;
    }
    if (!storeName || !email || !password) {
      this.onboardingError = 'Completa nombre del negocio, correo y contraseña.';
      return;
    }
    if (password.length < 8) {
      this.onboardingError = 'La contraseña debe tener al menos 8 caracteres.';
      return;
    }
    if (password !== confirmPassword) {
      this.onboardingError = 'Las contraseñas no coinciden.';
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
            this.onboardingError = 'No pude completar la activacion.';
            return;
          }
          this.loginUser = result.username;
          this.loginPass = '';
          this.onboardingActive = false;
          this.onboardingToken = '';
          this.onboardingSessionId = '';
          this.clearOnboardingQuery();
          this.showAlert('Cuenta creada. Ya puedes iniciar sesion.', 'success');
        },
        error: (error: unknown) => {
          this.onboardingError = this.describeOnboardingError(error, 'No pude completar la activacion.');
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
    const map = new Map<string, ProductCategory>();
    for (const category of this.activeProductCategories) {
      map.set(category.name.trim().toLowerCase(), category);
    }
    for (const preset of this.categoryPresets) {
      const key = preset.name.trim().toLowerCase();
      if (!map.has(key)) {
        map.set(key, {
          id: -map.size - 1,
          name: preset.name,
          description: preset.description,
          sizeLabel: preset.sizeLabel,
          active: true,
          createdAt: '',
        });
      }
    }
    return [...map.values()];
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
    return this.productCategoryOptions.map((category) => {
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
        const discountDiff = this.promotionDiscountAmount(b, this.cartSubtotal) - this.promotionDiscountAmount(a, this.cartSubtotal);
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
    return this.promotionDiscountAmount(promo, Math.max(this.cartSubtotal - this.manualCartDiscount, 0));
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
    return this.salesToday.filter((sale) => sale.status !== 'PENDING' && sale.status !== 'CANCELLED');
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
        title: 'Ventas pendientes',
        detail: `${this.pendingSalesCount} ticket(s) siguen pendientes de confirmar.`,
      });
    }

    if (Math.abs(this.cashDifference) >= 0.01) {
      alerts.push({
        tone: this.cashDifferenceSeverity,
        title: 'Diferencia en caja',
        detail: `La diferencia actual es de ${this.formatMoney(this.cashDifference)}.`,
      });
    }

    if (this.refundedToday.length > 0) {
      alerts.push({
        tone: this.refundedTodayTotal >= 500 ? 'risk' : 'warn',
        title: 'Devoluciones registradas',
        detail: `${this.refundedToday.length} devolucion(es) por ${this.formatMoney(this.refundedTodayTotal)}.`,
      });
    }

    const adjustments = this.reportInventoryMovements.filter((item) => item.type === 'ADJUSTMENT').length;
    if (adjustments > 0) {
      alerts.push({
        tone: adjustments >= 3 ? 'risk' : 'warn',
        title: 'Ajustes de inventario',
        detail: `${adjustments} ajuste(s) de inventario impactaron este dia.`,
      });
    }

    if (!alerts.length) {
      alerts.push({
        tone: 'good',
        title: 'Corte sano',
        detail: 'No se detectaron pendientes ni incidencias fuertes para esta fecha.',
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
        title: 'Vendido neto',
        current: this.formatMoney(this.todayTotal),
        previous: this.formatMoney(this.yesterdayTotal),
        detail: this.describeMoneyDelta(this.todayTotal, this.yesterdayTotal),
        tone: this.deltaTone(this.todayTotal, this.yesterdayTotal),
      },
      {
        title: 'Utilidad',
        current: this.formatMoney(this.todayProfit),
        previous: this.formatMoney(this.yesterdayProfit),
        detail: this.describeMoneyDelta(this.todayProfit, this.yesterdayProfit),
        tone: this.deltaTone(this.todayProfit, this.yesterdayProfit),
      },
      {
        title: 'Tickets cobrados',
        current: String(this.confirmedSalesToday.length),
        previous: String(this.confirmedSalesYesterday.length),
        detail: this.describeCountDelta(
          this.confirmedSalesToday.length,
          this.confirmedSalesYesterday.length,
          'ticket',
          false,
        ),
        tone: this.deltaTone(this.confirmedSalesToday.length, this.confirmedSalesYesterday.length),
      },
      {
        title: 'Devoluciones',
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
        label: 'Pendientes',
        count: this.pendingSalesCount,
        panel: 'tickets',
      },
      {
        id: 'REFUNDS',
        label: 'Devoluciones',
        count: this.refundedToday.length,
        panel: 'refunds',
      },
      {
        id: 'CANCELLED',
        label: 'Canceladas',
        count: this.cancelledSalesCount,
        panel: 'tickets',
      },
      {
        id: 'ADJUSTMENTS',
        label: 'Ajustes inventario',
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
    return preferred ? this.paymentLabel(preferred) : 'Sin preferencia';
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
      return 'Cliente VIP';
    }
    if (this.customerConfirmedSalesCount >= 4) {
      return 'Cliente frecuente';
    }
    if (this.customerConfirmedSalesCount >= 1) {
      return 'Cliente activo';
    }
    return 'Cliente nuevo';
  }

  get customerRecencyLabel(): string {
    const lastPurchase = this.customerLastPurchase;
    if (!lastPurchase) {
      return 'Aun sin compras cerradas';
    }

    const diffMs = Date.now() - new Date(lastPurchase.createdAt).getTime();
    const diffDays = Math.max(Math.floor(diffMs / 86400000), 0);
    if (diffDays === 0) return 'Compro hoy';
    if (diffDays === 1) return 'Compro ayer';
    if (diffDays < 7) return `Compro hace ${diffDays} dias`;
    if (diffDays < 30) return `Compro hace ${Math.floor(diffDays / 7)} semana(s)`;
    return `Compro hace ${Math.floor(diffDays / 30)} mes(es)`;
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
    return (
      {
        ALL: 'Todos',
        PENDING: 'Pendientes',
        REFUNDS: 'Devoluciones',
        CANCELLED: 'Canceladas',
        ADJUSTMENTS: 'Ajustes de inventario',
      } as const
    )[this.reportIncidentFilter];
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
    const labels: Record<ViewId, Record<string, string>> = {
      pos: {
        products: 'Productos',
        sale: 'Venta actual',
        ticket: 'Ticket listo',
      },
      products: {
        form: 'Alta de producto',
        catalog: 'Catalogo',
      },
      catalog: {
        products: 'Productos',
      },
      categories: {
        categories: 'Categorias',
      },
      inventory: {
        summary: 'Resumen',
        purchases: 'Compras',
      },
      customers: {
        form: 'Nuevo cliente',
        list: 'Listado',
        history: 'Historial',
      },
      promos: {
        form: 'Nueva promo',
        list: 'Listado',
      },
      reports: {
        summary: 'Resumen',
        sales: 'Ventas',
        tickets: 'Tickets',
        refunds: 'Devoluciones',
        movements: 'Movimientos',
        history: 'Historial',
      },
      settings: {
        profile: 'Configuracion',
      },
    };
    return labels[view][section] ?? '';
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
    const titles: Record<ViewId, string> = {
      pos: 'Punto de venta',
      products: 'Productos',
      catalog: 'Productos',
      categories: 'Categorias',
      inventory: 'Inventario',
      customers: 'Clientes',
      promos: 'Promos',
      reports: 'Corte diario',
      settings: 'Configuracion',
    };
    return titles[this.activeView];
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
      t.push(`${this.pendingSales.length} pago(s) pendiente(s) por confirmar`);
    }
    for (const p of this.products.filter((product) => product.stock <= 2)) {
      t.push(`Reponer ${p.name} (${p.stock} uds)`);
    }
    if (t.length === 0) t.push('Sin novedades');
    return t;
  }

  get stats() {
    return [
      {
        label: 'Venta actual',
        value: this.formatMoney(this.cartTotal),
        trend: `${this.cart.length} partidas`,
      },
      {
        label: 'Productos activos',
        value: String(this.products.filter((product) => product.status !== 'ARCHIVED').length),
        trend: 'Catalogo',
      },
      {
        label: 'Pendientes',
        value: String(this.pendingSales.length),
        trend: this.pendingSales.length ? 'Por confirmar' : 'Sin pendientes',
      },
      {
        label: 'Metodo pago',
        value: this.paymentLabel(this.selectedPayment),
        trend: this.activeCartPromo ? `Promo: ${this.activeCartPromo.code}` : 'Seleccionado',
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
      this.loginError = 'Escribe usuario y contraseña';
      return;
    }

    this.loginError = '';
    this.loginLoading = true;
    this.refresh
      .track(
        'Cargando...',
        this.http
          .post<LoginResponse>(this.loginEndpoint, { username, password })
          .pipe(timeout({ first: LOGIN_TIMEOUT_MS })),
      )
      .pipe(finalize(() => (this.loginLoading = false)))
      .subscribe({
        next: (result) => {
          if (!result.valid || !result.token) {
            this.loginError = 'Usuario o contraseña incorrectos';
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
        },
        error: (error: unknown) => {
          this.sessionToken = '';
          const backendUrl = this.apiBase.replace(/\/api$/, '');
          this.loginError =
            error instanceof HttpErrorResponse && error.status === 429
              ? 'Demasiados intentos. Espera unos minutos e intenta de nuevo.'
              : error instanceof Error && error.name === 'TimeoutError'
              ? `El backend no respondio. Revisa que este corriendo en ${backendUrl}`
              : `No pude conectar con el backend. Revisa ${backendUrl}`;
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
      this.recoveryError = 'Escribe tu correo o usuario';
      this.recoveryInfo = '';
      return;
    }

    this.recoveryError = '';
    this.recoveryInfo = '';
    this.recoveryLoading = true;
    this.refresh
      .track(
        'Enviando enlace...',
        this.http
          .post<PasswordResetRequestResponse>(this.passwordResetRequestEndpoint, { username })
          .pipe(timeout({ first: LOGIN_TIMEOUT_MS })),
      )
      .pipe(finalize(() => (this.recoveryLoading = false)))
      .subscribe({
        next: () => {
          this.recoveryInfo = 'Si el correo existe, te enviamos un enlace para restablecer la contraseña.';
          this.recoveryError = '';
        },
        error: (error: unknown) => {
          this.recoveryInfo = '';
          this.recoveryError =
            error instanceof HttpErrorResponse && error.status === 429
              ? 'Demasiados intentos. Espera unos minutos antes de intentar de nuevo.'
              : error instanceof HttpErrorResponse && error.status === 401
              ? 'El backend rechazo la solicitud de recuperacion. Revisa que esa ruta este publica y reinicia el backend.'
              : 'No pude iniciar la recuperación en este momento.';
        },
      });
  }

  completePasswordReset(): void {
    const token = this.recoveryToken.trim();
    const newPassword = this.recoveryPass.trim();
    const confirmPassword = this.recoveryConfirmPass.trim();

    if (!token) {
      this.recoveryError = 'El enlace de recuperación ya no es válido.';
      this.recoveryInfo = '';
      return;
    }

    if (!newPassword || !confirmPassword) {
      this.recoveryError = 'Escribe y confirma la nueva contraseña';
      this.recoveryInfo = '';
      return;
    }

    if (newPassword !== confirmPassword) {
      this.recoveryError = 'Las contraseñas no coinciden';
      this.recoveryInfo = '';
      return;
    }

    this.recoveryError = '';
    this.recoveryInfo = '';
    this.recoveryLoading = true;
    this.refresh
      .track(
        'Actualizando contraseña...',
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
          this.recoveryInfo = 'Contraseña actualizada. Ya puedes iniciar sesión.';
          this.recoveryError = '';
          this.showAlert('Contraseña actualizada. Ya puedes iniciar sesión.', 'success');
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
    this.onboardingInfo = 'Validando pago con Stripe...';
    this.onboardingSessionId = sessionId;

    this.http
      .post<OnboardingStartResponse>(this.onboardingStartEndpoint, { sessionId })
      .pipe(
        timeout({ first: ONBOARDING_TIMEOUT_MS }),
        finalize(() => {
          this.ngZone.run(() => {
            this.onboardingLoading = false;
          });
        })
      )
      .subscribe({
        next: (result) => {
          this.ngZone.run(() => {
            if (!result.ready || !result.onboardingToken) {
              this.onboardingError = 'No pude preparar la activacion.';
              return;
            }
            this.onboardingToken = result.onboardingToken;
            this.onboardingEmail = result.email || '';
            this.onboardingForm.email = result.email || '';
            this.onboardingInfo = 'Pago confirmado. Completa los datos de tu empresa para activar el acceso.';
            this.clearOnboardingQuery();
          });
        },
        error: (error: unknown) => {
          this.ngZone.run(() => {
            this.onboardingError = this.describeOnboardingError(error, 'No pude validar el pago con Stripe.');
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
            this.recoveryError = 'El enlace de recuperación ya no es válido.';
            return;
          }
          this.recoveryMaskedEmail = result.email || '';
          this.recoveryInfo = 'Crea una nueva contraseña para continuar.';
        },
        error: (error: unknown) => {
          this.recoveryError = this.describePasswordResetError(error);
        },
      });
  }

  setView(view: ViewId, section?: ViewSectionId): void {
    this.refresh.flash(`Cargando ${this.viewLabel(view)}...`);
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
    this.statusMessage = 'Listo para vender';
    this.setView('pos', 'sale');
  }

  addToCart(product: Product): void {
    if (product.status === 'ARCHIVED') {
      this.statusMessage = `${product.name} esta archivado`;
      return;
    }
    if (product.stock <= this.quantityInCart(product.id)) {
      this.statusMessage = `Sin stock disponible para ${product.name}`;
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
    this.statusMessage = `${product.name} agregado al carrito`;
  }

  removeFromCart(productId: number): void {
    this.cart = this.cart
      .map((item) => (item.productId === productId ? { ...item, qty: item.qty - 1 } : item))
      .filter((item) => item.qty > 0);
    this.statusMessage = 'Carrito actualizado';
  }

  clearCart(): void {
    this.cart = [];
    this.selectedPromoId = null;
    this.checkoutDiscount = 0;
    this.cashReceived = 0;
    this.statusMessage = 'Venta limpiada';
  }

  selectPayment(method: PaymentMethod): void {
    this.selectedPayment = method;
    this.statusMessage = `Metodo seleccionado: ${this.paymentLabel(method)}`;
  }

  selectCustomer(customerId: number | null): void {
    this.selectedCustomerId = customerId;
    this.syncSelectedPromo();
    this.statusMessage = `Cliente: ${
      customerId
        ? (this.customers.find((customer) => customer.id === customerId)?.name ?? 'Mostrador')
        : 'Mostrador'
    }`;
  }

  applyPromo(promoId: string | null): void {
    if (!promoId) {
      this.selectedPromoId = null;
      this.statusMessage = 'Promo removida';
      return;
    }

    const promo = this.promotions.find((item) => item.id === promoId);
    if (!promo) {
      this.selectedPromoId = null;
      this.statusMessage = 'La promo ya no existe';
      return;
    }
    if (!this.isPromotionApplicable(promo)) {
      this.selectedPromoId = null;
      this.statusMessage = 'Esa promo no aplica a la venta actual';
      return;
    }

    this.selectedPromoId = promo.id;
    this.statusMessage = `Promo aplicada: ${promo.name}`;
  }

  checkout(): void {
    if (!this.cart.length || this.isCharging) {
      this.statusMessage = 'Agrega productos antes de cobrar';
      return;
    }

    this.isCharging = true;
    this.refresh
      .track(
        'Procesando venta...',
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
              ? `Pago con ${this.paymentLabel(sale.paymentMethod)} pendiente de confirmar.${this.settings.autoOpenTicket ? ' Ticket abierto.' : ''}`
              : this.settings.autoOpenTicket
                ? 'Venta cobrada. Ticket abierto para imprimir.'
                : 'Venta cobrada. Ticket listo.';
          this.isCharging = false;
          this.loadProducts();
          this.loadSalesToday();
          this.loadPendingSales();
          this.refreshReportData();
        },
        error: () => {
          this.statusMessage = 'No se pudo cobrar. Revisa backend o stock.';
          this.isCharging = false;
        },
      });
  }

  confirmSale(id: number): void {
    this.refresh
      .track(
        'Confirmando pago...',
        this.http.post<SaleRecord>(this.apiUrl(`/sales/${id}/confirm`), {}),
      )
      .subscribe({
        next: (sale) => {
          this.statusMessage = 'Pago confirmado';
          this.lastTicket = sale;
          this.loadSalesToday();
          this.loadPendingSales();
          this.refreshReportData();
        },
        error: () => {
          this.statusMessage = 'No se pudo confirmar el pago';
        },
      });
  }

  cancelSale(id: number): void {
    this.refresh
      .track(
        'Cancelando venta...',
        this.http.post<SaleRecord>(this.apiUrl(`/sales/${id}/cancel`), {}),
      )
      .subscribe({
        next: () => {
          this.statusMessage = 'Venta pendiente cancelada y stock repuesto';
          this.loadProducts();
          this.loadSalesToday();
          this.loadPendingSales();
          this.refreshReportData();
        },
        error: () => {
          this.statusMessage = 'No se pudo cancelar la venta';
        },
      });
  }

  refundSale(id: number): void {
    const draft = this.refundDrafts[id] ?? {};
    const items = Object.entries(draft)
      .map(([saleItemId, quantity]) => ({ saleItemId: Number(saleItemId), quantity }))
      .filter((item) => item.quantity > 0);

    if (!items.length) {
      this.statusMessage = 'Selecciona al menos una pieza para devolver o usa "Todo"';
      return;
    }

    this.refresh
      .track(
        'Procesando devolucion...',
        this.http.post<SaleRecord>(this.apiUrl(`/sales/${id}/refund`), { items }),
      )
      .subscribe({
        next: (sale) => {
          this.statusMessage =
            sale.status === 'REFUNDED'
              ? `Venta #${sale.id} devuelta, stock repuesto y corte ajustado`
              : `Venta #${sale.id} actualizada con devolucion parcial y corte ajustado`;
          this.lastTicket = sale;
          this.clearRefundDraft(id);
          this.loadProducts();
          this.loadSalesToday();
          this.loadPendingSales();
          this.refreshReportData();
        },
        error: () => {
          this.statusMessage = 'No se pudo procesar la devolucion';
        },
      });
  }

  refundAllRemaining(id: number): void {
    this.refresh
      .track(
        'Procesando devolucion...',
        this.http.post<SaleRecord>(this.apiUrl(`/sales/${id}/refund`), null),
      )
      .subscribe({
        next: (sale) => {
          this.statusMessage =
            sale.status === 'REFUNDED'
              ? `Venta #${sale.id} devuelta, stock repuesto y corte ajustado`
              : `Venta #${sale.id} actualizada con devolucion parcial y corte ajustado`;
          this.lastTicket = sale;
          this.clearRefundDraft(id);
          this.loadProducts();
          this.loadSalesToday();
          this.loadPendingSales();
          this.refreshReportData();
        },
        error: () => {
          this.statusMessage = 'No se pudo procesar la devolucion';
        },
      });
  }

  createProduct(): void {
    if (!this.productForm.name.trim()) {
      this.statusMessage = 'El producto necesita nombre';
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
      .track(this.editingProductId ? 'Actualizando producto...' : 'Guardando producto...', request)
      .subscribe({
        next: () => {
          this.statusMessage = this.editingProductId ? 'Producto actualizado' : 'Producto creado';
          this.closeProductForm();
          this.loadProducts();
        },
        error: () => {
          this.statusMessage = this.editingProductId
            ? 'No se pudo actualizar el producto'
            : 'No se pudo crear el producto';
        },
      });
  }

  createCategory(): void {
    if (!this.categoryForm.name.trim()) {
      this.statusMessage = 'La categoria necesita nombre';
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
        this.editingCategoryId ? 'Actualizando categoria...' : 'Guardando categoria...',
        request,
      )
      .subscribe({
        next: () => {
          this.statusMessage = this.editingCategoryId
            ? 'Categoria actualizada'
            : 'Categoria creada';
          this.resetCategoryForm();
          this.loadProductCategories();
        },
        error: () => {
          this.statusMessage = this.editingCategoryId
            ? 'No se pudo actualizar la categoria'
            : 'No se pudo crear la categoria';
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
    if (!window.confirm(`Eliminar categoria ${category.name}?`)) return;
    this.refresh
      .track(
        'Eliminando categoria...',
        this.http.delete(this.apiUrl(`/product-categories/${category.id}`)),
      )
      .subscribe({
        next: () => {
          this.statusMessage = 'Categoria eliminada';
          if (this.productForm.category === category.name) {
            this.productForm.category = '';
          }
          this.loadProductCategories();
        },
        error: () => {
          this.statusMessage = 'No se pudo eliminar la categoria. Revisa si ya tiene productos.';
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
    this.statusMessage = `Categoria seleccionada: ${name}`;
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
    this.statusMessage = `Preset cargado para ${preset.name}. Guardalo en Categorias para dejarlo fijo.`;
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
    this.productImageFileName = product.imageUrl ? 'Imagen cargada' : '';
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
      this.statusMessage = `Imagen lista: ${file.name}`;
    };
    reader.onerror = () => {
      this.statusMessage = 'No se pudo leer la imagen';
    };
    reader.readAsDataURL(file);
  }

  clearProductImage(): void {
    this.productForm.imageUrl = '';
    this.productImageFileName = '';
    this.statusMessage = 'Imagen eliminada del formulario';
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
      .track('Eliminando producto...', this.http.delete(this.apiUrl(`/products/${product.id}`)))
      .subscribe({
        next: () => {
          this.statusMessage = `${product.name} eliminado`;
          if (this.editingProductId === product.id) {
            this.resetProductForm();
          }
          this.loadProducts();
        },
        error: () => {
          this.statusMessage = 'No se pudo eliminar el producto';
        },
      });
  }

  restock(product: Product, quantity: number): void {
    this.adjustInventory(
      product.id,
      quantity,
      `Ajuste manual para ${product.name}`,
      'Stock actualizado',
    );
  }

  reduceStock(product: Product): void {
    this.adjustInventory(
      product.id,
      -1,
      `Ajuste manual para ${product.name}`,
      'Ajuste de inventario guardado',
    );
  }

  registerPurchase(): void {
    if (!this.purchaseForm.productId) {
      this.statusMessage = 'Selecciona un producto para registrar la compra';
      return;
    }
    if (this.purchaseForm.quantity < 1) {
      this.statusMessage = 'La compra necesita al menos 1 unidad';
      return;
    }

    this.refresh
      .track(
        'Registrando compra...',
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
          this.statusMessage = 'Compra registrada y stock actualizado';
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
          this.statusMessage = 'No se pudo registrar la compra';
        },
      });
  }

  addCustomer(): void {
    if (!this.newCustomerName.trim()) {
      this.statusMessage = 'El cliente necesita nombre';
      return;
    }
    this.refresh
      .track(
        'Guardando cliente...',
        this.http.post<Customer>(this.apiUrl('/customers'), {
          name: this.newCustomerName,
          phone: this.newCustomerPhone || null,
          notes: this.newCustomerNotes || null,
        }),
      )
      .subscribe({
        next: () => {
          this.statusMessage = 'Cliente agregado';
          this.newCustomerName = '';
          this.newCustomerPhone = '';
          this.newCustomerNotes = '';
          this.loadCustomers();
        },
        error: () => {
          this.statusMessage = 'No se pudo agregar el cliente';
        },
      });
  }

  savePromo(): void {
    const name = this.promoForm.name.trim();
    const code = this.normalizePromoCode(this.promoForm.code || name);
    const value = Math.max(Number(this.promoForm.value) || 0, 0);
    const minSubtotal = Math.max(Number(this.promoForm.minSubtotal) || 0, 0);

    if (!name) {
      this.statusMessage = 'La promo necesita nombre';
      return;
    }
    if (!code) {
      this.statusMessage = 'La promo necesita codigo';
      return;
    }
    if (value <= 0) {
      this.statusMessage = 'La promo necesita un valor mayor a 0';
      return;
    }
    if (this.promoForm.type === 'PERCENT' && value > 100) {
      this.statusMessage = 'El porcentaje maximo es 100';
      return;
    }
    if (
      this.promoForm.endsAt &&
      this.promoForm.startsAt &&
      this.promoForm.endsAt < this.promoForm.startsAt
    ) {
      this.statusMessage = 'La fecha final no puede ser menor a la inicial';
      return;
    }
    if (
      this.promotions.some(
        (promo) => promo.id !== this.editingPromoId && promo.code.toUpperCase() === code,
      )
    ) {
      this.statusMessage = 'Ese codigo ya existe';
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
    this.statusMessage = this.editingPromoId ? 'Promo actualizada' : 'Promo guardada';
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
    if (!window.confirm(`Eliminar promo ${promo.name}?`)) return;
    this.promotions = this.promotions.filter((item) => item.id !== promo.id);
    if (this.selectedPromoId === promo.id) {
      this.selectedPromoId = null;
    }
    if (this.editingPromoId === promo.id) {
      this.resetPromoForm();
    }
    this.persistPromotions();
    this.statusMessage = 'Promo eliminada';
  }

  deleteCustomer(customer: Customer): void {
    this.refresh
      .track('Eliminando cliente...', this.http.delete(this.apiUrl(`/customers/${customer.id}`)))
      .subscribe({
        next: () => {
          this.statusMessage = `${customer.name} eliminado`;
          if (this.selectedCustomerId === customer.id) this.selectedCustomerId = null;
          if (this.selectedCustomerHistory?.id === customer.id) this.selectedCustomerHistory = null;
          this.loadCustomers();
        },
        error: () => {
          this.statusMessage = 'No se pudo eliminar el cliente';
        },
      });
  }

  showCustomerSales(customer: Customer): void {
    this.selectedCustomerHistory = customer;
    this.activeSections = { ...this.activeSections, customers: 'history' };
    this.refresh
      .track(
        'Cargando historial...',
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
      this.settingsMessage = 'El nombre de la tienda es obligatorio';
      this.statusMessage = this.settingsMessage;
      return;
    }

    this.isSavingTicketSettings = true;
    this.refresh
      .track(
        'Guardando datos del ticket...',
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
          this.settingsMessage = 'Datos del ticket guardados';
          this.statusMessage = 'Datos del ticket guardados';
        },
        error: () => {
          this.settingsMessage = 'No se pudieron guardar los datos del ticket';
          this.statusMessage = this.settingsMessage;
        },
      });
  }

  saveCredentials(): void {
    if (!this.credentialsForm.username.trim()) {
      this.credentialsMessage = 'El usuario es obligatorio';
      this.statusMessage = this.credentialsMessage;
      return;
    }
    if (!this.credentialsForm.currentPassword.trim()) {
      this.credentialsMessage = 'Escribe la contrasena actual para confirmar el cambio';
      this.statusMessage = this.credentialsMessage;
      return;
    }

    this.isSavingCredentials = true;
    this.refresh
      .track(
        'Guardando acceso...',
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
          this.credentialsMessage = 'Usuario y contrasena guardados';
          this.statusMessage = 'Acceso actualizado';
        },
        error: () => {
          this.credentialsMessage = 'No se guardo: revisa la contrasena actual';
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
      this.settingsMessage = 'Logo cargado, guarda la configuracion';
    };
    reader.readAsDataURL(file);
  }

  downloadBackup(): void {
    this.runBackupExport('Generando respaldo Excel...', (backup) => {
      const payload = this.buildBackupWorkbook(backup);
      const blob = new Blob([payload], { type: 'application/vnd.ms-excel;charset=utf-8' });
      this.downloadBlob(blob, `boutique-os-respaldo-${this.todayDateString()}.xls`);
      this.settingsMessage = 'Backup formal descargado en Excel';
    });
  }

  downloadBackupCsv(): void {
    this.runBackupExport('Generando respaldo CSV...', (backup) => {
      const payload = this.buildBackupCsv(backup);
      const blob = new Blob([payload], { type: 'text/csv;charset=utf-8' });
      this.downloadBlob(blob, `boutique-os-respaldo-${this.todayDateString()}.csv`);
      this.settingsMessage = 'Backup formal descargado en CSV';
    });
  }

  downloadBackupPdf(): void {
    this.runBackupExport('Generando respaldo PDF...', async (backup) => {
      await this.openBackupPdf(backup);
      this.settingsMessage = 'Backup formal descargado en PDF';
    });
  }

  exportDailyReportPdf(): void {
    void this.openDailyReportPdf();
  }

  saveCashCount(): void {
    if (this.reportDayClosed) {
      this.statusMessage = 'El dia esta cerrado. Reabrelo para editar el corte.';
      return;
    }
    this.isSavingCashCount = true;
    this.refresh
      .track(
        'Guardando corte...',
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
          this.statusMessage = 'Corte de efectivo guardado';
          this.isSavingCashCount = false;
        },
        error: () => {
          this.statusMessage = 'No se pudo guardar el efectivo real';
          this.isSavingCashCount = false;
        },
      });
  }

  closeReportDay(): void {
    if (this.reportDayClosed) {
      this.statusMessage = 'Este dia ya estaba cerrado';
      return;
    }
    this.isClosingReportDay = true;
    this.refresh
      .track(
        'Cerrando dia...',
        this.http.post<DailyCashCount>(
          this.apiUrl(`/reports/cash-count/today/close?date=${this.reportDate}`),
          {},
        ),
      )
      .subscribe({
        next: (cashCount) => {
          this.applyDailyCashCount(cashCount);
          this.statusMessage = 'Dia cerrado correctamente';
          this.isClosingReportDay = false;
        },
        error: () => {
          this.statusMessage = 'No se pudo cerrar el dia';
          this.isClosingReportDay = false;
        },
      });
  }

  reopenReportDay(): void {
    if (!this.reportDayClosed) {
      this.statusMessage = 'Ese dia ya esta abierto';
      return;
    }
    this.isReopeningReportDay = true;
    this.refresh
      .track(
        'Reabriendo dia...',
        this.http.post<DailyCashCount>(
          this.apiUrl(`/reports/cash-count/today/reopen?date=${this.reportDate}`),
          {},
        ),
      )
      .subscribe({
        next: (cashCount) => {
          this.applyDailyCashCount(cashCount);
          this.statusMessage = 'Dia reabierto';
          this.isReopeningReportDay = false;
        },
        error: () => {
          this.statusMessage = 'No se pudo reabrir el dia';
          this.isReopeningReportDay = false;
        },
      });
  }

  openLastTicketPdf(): void {
    if (!this.lastTicket) {
      this.statusMessage = 'No hay ticket reciente para imprimir';
      return;
    }
    void this.openTicketPdf(this.lastTicket);
    this.statusMessage = 'PDF del ticket abierto';
  }

  openSaleTicketPdf(sale: SaleRecord): void {
    void this.openTicketPdf(sale);
    this.statusMessage = `PDF del ticket #${sale.id} abierto`;
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
    return this.paymentMethods.find((item) => item.value === method)?.label ?? method;
  }

  saleStatusLabel(status: SaleStatus): string {
    if (status === 'PENDING') return 'Pendiente';
    if (status === 'PARTIALLY_REFUNDED') return 'Parcialmente devuelta';
    if (status === 'CANCELLED') return 'Cancelada';
    if (status === 'REFUNDED') return 'Devuelta';
    return 'Confirmada';
  }

  customerWhatsappHref(phone: string): string {
    const normalized = phone.replace(/\D/g, '');
    return normalized ? `https://wa.me/${normalized}` : '#';
  }

  promotionTypeLabel(type: PromotionType): string {
    return type === 'PERCENT' ? 'Porcentaje' : 'Monto fijo';
  }

  promotionValueLabel(promo: Promotion): string {
    return promo.type === 'PERCENT' ? `${promo.value}%` : this.formatMoney(promo.value);
  }

  promotionScopeLabel(promo: Promotion): string {
    if (promo.customerId == null) return 'Aplica a cualquier cliente';
    const customer = this.customers.find((item) => item.id === promo.customerId);
    return customer ? `Solo para ${customer.name}` : 'Cliente especifico';
  }

  promotionWindowLabel(promo: Promotion): string {
    return promo.endsAt ? `${promo.startsAt} al ${promo.endsAt}` : `Desde ${promo.startsAt}`;
  }

  get ticketQrLabel(): string {
    return this.settingsForm.instagramHandle.trim() || this.settings.instagramHandle || '';
  }

  promotionStatusLabel(promo: Promotion): string {
    if (!promo.active) return 'Inactiva';
    if (this.isPromotionExpired(promo)) return 'Vencida';
    if (this.isPromotionFuture(promo)) return 'Programada';
    return 'Activa';
  }

  customerInitials(customer: Customer | null): string {
    if (!customer?.name.trim()) return 'CL';
    const parts = customer.name.trim().split(/\s+/).slice(0, 2);
    return parts.map((part) => part.charAt(0).toUpperCase()).join('');
  }

  productStatusLabel(status: ProductStatus): string {
    return this.productStatuses.find((item) => item.value === status)?.label ?? status;
  }

  inventoryMovementLabel(type: InventoryMovementType): string {
    if (type === 'PURCHASE') return 'Compra';
    if (type === 'SALE') return 'Venta';
    if (type === 'RETURN') return 'Devolucion';
    return 'Ajuste';
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
    if (this.settings.showAddressOnTicket && this.settings.address) center(this.settings.address, 7);
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
    center('TICKET DE VENTA', 9, 'bold');
    y += 1;
    row('Folio', ticketRef, true);
    row('Fecha', this.formatDateTime(sale.createdAt));
    if (sale.refundedAt) {
      row('Devuelta', this.formatDateTime(sale.refundedAt));
    }
    if (this.settings.showCustomerOnTicket) {
      row('Cliente', sale.customerName || 'Mostrador');
    }
    row('Pago', this.paymentLabel(sale.paymentMethod));
    row('Estado', this.saleStatusLabel(sale.status));
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
          ? `${item.quantity} x ${this.formatMoney(item.unitPrice)} (${item.refundedQuantity} dev.)`
          : `${item.quantity} x ${this.formatMoney(item.unitPrice)}`;
      doc.text(detail, margin, y);
      doc.text(this.formatMoney(item.lineTotal), pageWidth - margin, y, { align: 'right' });
      y += 5;
    }

    line();
    row('Subtotal', this.formatMoney(sale.subtotal));
    if (this.settings.showSavingsOnTicket && savings > 0) {
      row('Ahorro', `-${this.formatMoney(savings)}`);
    }
    if (sale.refundedTotal > 0) {
      row('Devuelto', `-${this.formatMoney(sale.refundedTotal)}`);
    }
    row('Total', this.formatMoney(sale.total), true);
    if (sale.paymentMethod === 'CASH') {
      row('Recibido', this.formatMoney(sale.cashReceived || 0));
      if (this.settings.showChangeOnTicket) {
        row('Cambio', this.formatMoney(sale.changeDue || 0), true);
      }
    }
    row('Piezas', String(sale.items.reduce((sum, item) => sum + item.quantity, 0)));
    y += 3;
    line();
    const qrDataUrl = await this.generateTicketQrDataUrl();
    if (qrDataUrl && this.ticketQrLabel) {
      const qrSize = this.settings.ticketPaperSize === 'HALF_LETTER' ? 28 : 22;
      ensureSpace(qrSize + 16);
      try {
        doc.addImage(qrDataUrl, 'PNG', (pageWidth - qrSize) / 2, y, qrSize, qrSize, undefined, 'FAST');
        y += qrSize + 4;
        center(this.ticketQrLabel, 7, 'bold');
      } catch {
        y += 2;
      }
      line();
    }
    center(this.settings.thankYouMessage || 'Gracias por tu compra', 8, 'bold');
    if (this.settings.ticketFooterNote) {
      center(this.settings.ticketFooterNote, 7);
    }

    const blobUrl = doc.output('bloburl');
    const printWindow = window.open(blobUrl, '_blank', 'noopener,noreferrer');

    if (!printWindow) {
      doc.save(this.ticketFilename(sale));
      this.statusMessage = 'No pude abrir la ventana. Descargue el PDF del ticket.';
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
    doc.text(`Corte diario · ${this.settings.storeName || 'Boutique OS'}`, margin, y);
    y += 7;
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(9);
    doc.text(`Fecha: ${this.reportDate}`, margin, y);
    doc.text(`Generado: ${this.formatDateTime(new Date().toISOString())}`, pageWidth - margin, y, {
      align: 'right',
    });
    y += 10;

    section('Resumen ejecutivo');
    row('Vendido neto', this.formatMoney(this.todayTotal), true);
    row('Utilidad neta', this.formatMoney(this.todayProfit));
    row('Tickets cobrados', String(this.confirmedSalesToday.length));
    row('Tickets pendientes', String(this.pendingSalesCount));
    row('Caja total esperada', this.formatMoney(this.expectedBoxTotal));
    row('Efectivo esperado', this.formatMoney(this.cashExpected));
    row('Efectivo real', this.formatMoney(this.actualCashInput || 0));
    row('Diferencia', this.formatMoney(this.cashDifference), true);

    section('Comparacion vs ayer');
    for (const item of this.reportComparisonItems) {
      paragraph(`${item.title}: hoy ${item.current} · ayer ${item.previous} · ${item.detail}`);
    }

    section('Metricas');
    row('Ticket promedio', this.formatMoney(this.averageTicketToday));
    row('Piezas vendidas', String(this.piecesSoldToday));
    row('Margen promedio', `${this.averageMarginToday.toFixed(1)}%`);
    row('Hora pico', this.peakHourLabel);
    row(
      'Top producto',
      this.topSellingProductToday
        ? `${this.topSellingProductToday.name} · ${this.topSellingProductToday.qty} uds`
        : 'Sin ventas confirmadas',
    );

    section('Pago por metodo');
    for (const item of this.paymentSummary) {
      paragraph(
        `${item.label}: ${this.formatMoney(item.total)} · ${item.count} venta(s) · promedio ${this.formatMoney(item.average)}${item.refunds ? ` · ${item.refunds} devol.` : ''}`,
      );
    }

    section('Alertas e incidencias');
    for (const alert of this.reportAlerts) {
      paragraph(`${alert.title}: ${alert.detail}`, 8.5, alert.tone === 'risk' ? 'bold' : 'normal');
    }

    section('Corte de caja');
    row('Diferencia actual', this.formatMoney(this.cashDifference), true);
    row('Estado del dia', this.reportDayClosed ? 'Cerrado' : 'Abierto');
    row('Cerrado a las', this.reportClosedAt ? this.formatDateTime(this.reportClosedAt) : 'Sin cierre');
    row('Ultimo guardado', this.cashCountUpdatedAt ? this.formatDateTime(this.cashCountUpdatedAt) : 'Sin guardar');
    paragraph(`Notas: ${this.cashCountNotes || 'Sin notas registradas.'}`);

    if (this.topProductsToday.length) {
      section('Productos mas vendidos');
      for (const item of this.topProductsToday) {
        row(item.name, `${item.qty} uds`);
      }
    }

    doc.save(`corte-diario-${this.reportDate}.pdf`);
    this.statusMessage = 'PDF del corte diario descargado';
  }

  async refreshTicketQrPreview(): Promise<void> {
    const seq = ++this.ticketQrRefreshSeq;
    const nextDataUrl = await this.generateTicketQrDataUrl();
    if (seq !== this.ticketQrRefreshSeq) {
      return;
    }
    this.ticketQrDataUrl = nextDataUrl;
  }

  private async generateTicketQrDataUrl(): Promise<string> {
    const handle = this.normalizeInstagramHandle(this.settingsForm.instagramHandle || this.settings.instagramHandle);
    if (!handle) return '';
    try {
      const QRCode = await import('qrcode');
      return await QRCode.toDataURL(`https://instagram.com/${handle}`, {
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

  private normalizeInstagramHandle(value: string): string {
    return value.trim().replace(/^@+/, '').replace(/\s+/g, '');
  }

  private loadProducts(): void {
    this.http.get<Product[]>(this.apiUrl('/products')).subscribe({
      next: (products) => {
        this.products = products;
      },
      error: () => {
        this.statusMessage = 'No pude cargar productos del backend';
      },
    });
  }

  private buildBackupWorkbook(backup: Record<string, unknown>): string {
    const sections = this.backupSections(backup);
    const generatedAt = this.escapeHtml(String(backup['generatedAt'] || new Date().toISOString()));
    const body = sections
      .map(([title, data]) => this.backupSection(title, data))
      .join('');

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
    const headers = ['seccion', ...new Set(rows.flatMap((row) => Object.keys(row).filter((key) => key !== 'seccion')))];
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
            this.settingsMessage = 'No se pudo generar el backup';
            this.statusMessage = this.settingsMessage;
          });
        },
        error: () => {
          this.settingsMessage = 'No se pudo generar el backup';
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
    doc.text('Respaldo Boutique OS', margin, y);
    y += 7;
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(9);
    doc.text(`Generado: ${this.formatDateTime(String(backup['generatedAt'] || new Date().toISOString()))}`, margin, y);
    y += 8;

    for (const [section, rows] of this.backupSections(backup)) {
      ensureSpace(14);
      doc.setDrawColor(210, 218, 228);
      doc.setFillColor(232, 237, 243);
      doc.roundedRect(margin, y - 5, maxWidth, 8, 1.5, 1.5, 'FD');
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(11);
      doc.text(`${section} (${rows.length})`, margin + 3, y);
      y += 8;

      if (!rows.length) {
        doc.setFont('helvetica', 'normal');
        doc.setFontSize(8.5);
        doc.text('Sin datos', margin, y);
        y += 6;
        continue;
      }

      for (const row of rows.slice(0, 18)) {
        const flattened = this.flattenBackupRow(row);
        const summary = Object.entries(flattened)
          .slice(0, 5)
          .map(([key, value]) => `${this.humanizeBackupHeader(key)}: ${value}`)
          .join(' | ');
        const lines = doc.splitTextToSize(summary || 'Sin datos', maxWidth) as string[];
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
        doc.text(`... ${rows.length - 18} registro(s) mas en el origen. Usa CSV o Excel para el detalle completo.`, margin, y);
        y += 6;
      }

      y += 3;
    }

    doc.save(`boutique-os-respaldo-${this.todayDateString()}.pdf`);
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
        this.settingsMessage = 'No se pudo cargar la configuracion';
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
        this.statusMessage = 'No se pudieron cargar las categorias';
      },
    });
  }

  private loadSalesToday(): void {
    this.http.get<SaleRecord[]>(this.apiUrl(`/sales/today?date=${this.reportDate}`)).subscribe({
      next: (sales) => {
        this.salesToday = sales;
      },
      error: () => {
        this.statusMessage = 'No pude cargar el corte del dia';
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
        this.statusMessage = 'No pude cargar clientes del backend';
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
        'Actualizando inventario...',
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
          this.statusMessage = 'No se pudo actualizar inventario';
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
    const labels: Record<ViewId, string> = {
      pos: 'punto de venta',
      products: 'productos',
      catalog: 'catalogo',
      categories: 'categorias',
      inventory: 'inventario',
      customers: 'clientes',
      promos: 'promos',
      reports: 'corte diario',
      settings: 'configuracion',
    };
    return labels[view];
  }

  private showAlert(message: string, type?: AlertType): void {
    if (!message || message === 'Listo para vender') return;
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
      return 'La validacion con Stripe tardo demasiado. Reintenta en unos segundos.';
    }
    if (error instanceof HttpErrorResponse) {
      if (error.status === 400) return 'Stripe no confirmo un pago valido para esta activacion.';
      if (error.status === 404) return 'El enlace de activacion ya no es valido.';
      if (error.status === 409) return 'Esta cuenta ya fue activada o este pago ya se uso.';
      if (error.status === 410) return 'El enlace de activacion expiro. Vuelve desde Stripe.';
      if (error.status === 503) return 'Stripe no esta configurado todavia en el backend.';
    }
    return fallback;
  }

  private describePasswordResetError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 400) return 'La contraseña debe tener mayúscula, minúscula y al menos un número.';
      if (error.status === 404) return 'El enlace de recuperación ya no es válido.';
      if (error.status === 410) return 'El enlace de recuperación expiró. Solicita uno nuevo.';
      if (error.status === 429) return 'Demasiados intentos. Espera unos minutos antes de intentar de nuevo.';
    }
    if (error && typeof error === 'object' && 'name' in error && error.name === 'TimeoutError') {
      return 'La operación tardó demasiado. Intenta otra vez.';
    }
    return 'No pude completar la recuperación en este momento.';
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
      text.includes('revisa')
    ) {
      return 'error';
    }
    if (text.includes('pendiente') || text.includes('selecciona') || text.includes('actualizado')) {
      return 'warning';
    }
    if (
      text.includes('guardado') ||
      text.includes('agregado') ||
      text.includes('registrada') ||
      text.includes('confirmado') ||
      text.includes('descargado') ||
      text.includes('cobrada')
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

  private deltaTone(current: number, previous: number, lowerIsBetter = false): 'good' | 'warn' | 'risk' {
    if (current === previous) return 'warn';
    const improved = lowerIsBetter ? current < previous : current > previous;
    return improved ? 'good' : 'risk';
  }

  private describeMoneyDelta(current: number, previous: number, lowerIsBetter = false): string {
    const diff = current - previous;
    if (Math.abs(diff) < 0.01) {
      return 'Sin cambio contra ayer';
    }
    const direction = lowerIsBetter
      ? diff < 0
        ? 'menos que ayer'
        : 'mas que ayer'
      : diff > 0
        ? 'mas que ayer'
        : 'menos que ayer';
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
      return `Sin cambio en ${label}s`;
    }
    const direction = lowerIsBetter
      ? diff < 0
        ? 'menos que ayer'
        : 'mas que ayer'
      : diff > 0
        ? 'mas que ayer'
        : 'menos que ayer';
    return `${diff > 0 ? '+' : ''}${diff} ${label}(s) ${direction}`;
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
