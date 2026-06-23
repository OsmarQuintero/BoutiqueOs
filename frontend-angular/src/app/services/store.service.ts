import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { jsPDF } from 'jspdf';
import { finalize, timeout } from 'rxjs';
import { RefreshService } from './refresh.service';

export type PaymentMethod = 'CASH' | 'TRANSFER' | 'CARD';
export type SaleStatus = 'PENDING' | 'CONFIRMED' | 'PARTIALLY_REFUNDED' | 'CANCELLED' | 'REFUNDED';
export type ProductStatus = 'ACTIVE' | 'OUT_OF_STOCK' | 'ARCHIVED';

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
  updatedAt: string;
}

export type InventoryMovementType = 'PURCHASE' | 'SALE' | 'ADJUSTMENT' | 'RETURN';
export type ReportPanel = 'summary' | 'sales' | 'tickets' | 'refunds' | 'movements';
export type InventoryPanel = 'summary' | 'purchases';
export type PosSection = 'products' | 'sale' | 'ticket';
export type ProductsSection = 'form' | 'catalog';
export type CatalogSection = 'products';
export type CategoriesSection = 'categories';
export type CustomersSection = 'form' | 'list' | 'history';
export type SettingsSection = 'profile';
export type ViewSectionId =
  | PosSection
  | ProductsSection
  | CatalogSection
  | CategoriesSection
  | CustomersSection
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
  logoUrl: string;
  thankYouMessage: string;
  username: string;
  updatedAt: string;
}

interface LoginResponse {
  valid: boolean;
  token: string | null;
}

const LOGIN_TIMEOUT_MS = 8000;
const SAVE_TIMEOUT_MS = 4000;

export type ViewId =
  | 'pos'
  | 'products'
  | 'catalog'
  | 'categories'
  | 'inventory'
  | 'customers'
  | 'reports'
  | 'settings';
export type AlertType = 'success' | 'error' | 'warning' | 'info';

@Injectable({ providedIn: 'root' })
export class StoreService {
  readonly apiBase = this.resolveApiBase();

  private readonly loggedInState = signal(false);
  private readonly loginUserState = signal('');
  private readonly loginPassState = signal('');
  private readonly loginErrorState = signal('');
  private readonly loginLoadingState = signal(false);
  loginEndpoint = this.apiUrl('/settings/login');
  private sessionToken = '';
  isSavingTicketSettings = false;
  isSavingCredentials = false;
  settingsMessage = '';
  credentialsMessage = '';

  activeView: ViewId = 'pos';
  activeSections: Record<ViewId, ViewSectionId> = {
    pos: 'products',
    products: 'form',
    catalog: 'products',
    categories: 'categories',
    inventory: 'summary',
    customers: 'list',
    reports: 'summary',
    settings: 'profile',
  };
  products: Product[] = [];
  productCategories: ProductCategory[] = [];
  cart: CartItem[] = [];
  salesToday: SaleRecord[] = [];
  allSales: SaleRecord[] = [];
  refundsToday: SaleRefundRecord[] = [];
  pendingSales: SaleRecord[] = [];
  reportDate = this.todayDateString();
  reportPanel: ReportPanel = 'summary';
  selectedPayment: PaymentMethod = 'CASH';
  cashReceived = 0;
  private _statusMessage = 'Listo para vender';
  alertMessage = '';
  alertType: AlertType = 'info';
  private alertTimer: ReturnType<typeof setTimeout> | null = null;
  isCharging = false;
  searchTerm = '';
  customers: Customer[] = [];
  inventoryMovements: InventoryMovement[] = [];
  reportInventoryMovements: InventoryMovement[] = [];
  recentPurchases: PurchaseRecord[] = [];
  inventoryDate = this.todayDateString();
  inventoryPanel: InventoryPanel = 'summary';
  posCategoryFilter = 'ALL';
  inventoryCategoryFilter = 'ALL';
  selectedCustomerId: number | null = null;
  newCustomerName = '';
  newCustomerPhone = '';
  newCustomerNotes = '';
  selectedCustomerHistory: Customer | null = null;
  customerSales: SaleRecord[] = [];
  checkoutDiscount = 0;
  lastTicket: SaleRecord | null = null;
  ticketSearchTerm = '';
  refundDrafts: Record<number, Record<number, number>> = {};
  actualCashInput = 0;
  cashCountNotes = '';
  cashCountUpdatedAt: string | null = null;
  isSavingCashCount = false;
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
    logoUrl: '',
    thankYouMessage: 'Gracias por tu compra',
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
    logoUrl: '',
    address: '',
    thankYouMessage: 'Gracias por tu compra',
  };
  credentialsForm = {
    username: 'admin',
    currentPassword: '',
    newPassword: '',
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

  readonly navItems = [
    { id: 'pos' as ViewId, label: 'Punto de venta', icon: '🛒' },
    { id: 'catalog' as ViewId, label: 'Catalogos', icon: '🏷️' },
    { id: 'categories' as ViewId, label: 'Categorias', icon: '🗂️' },
    { id: 'inventory' as ViewId, label: 'Inventario', icon: '📋' },
    { id: 'customers' as ViewId, label: 'Clientes', icon: '👥' },
    { id: 'reports' as ViewId, label: 'Corte diario', icon: '📊' },
    { id: 'settings' as ViewId, label: 'Configuracion', icon: '⚙️' },
  ];

  readonly reportPanels = [
    { id: 'summary' as ReportPanel, label: 'Resumen' },
    { id: 'sales' as ReportPanel, label: 'Ventas' },
    { id: 'tickets' as ReportPanel, label: 'Tickets' },
    { id: 'refunds' as ReportPanel, label: 'Devoluciones' },
    { id: 'movements' as ReportPanel, label: 'Movimientos' },
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
  ) {}

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

  get cartSubtotal(): number {
    return this.cart.reduce((total, item) => total + item.qty * item.price, 0);
  }

  get cartDiscount(): number {
    return Math.min(Math.max(this.checkoutDiscount || 0, 0), this.cartSubtotal);
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
      return { ...method, total, count: sales.length, refunds: refunds.length };
    });
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

  get refundedToday(): SaleRefundRecord[] {
    return this.refundsToday;
  }

  get refundedTodayTotal(): number {
    return this.refundedToday.reduce((total, refund) => total + refund.total, 0);
  }

  get refundedTodayProfit(): number {
    return this.refundedToday.reduce((total, refund) => total + refund.estimatedProfit, 0);
  }

  get filteredTicketHistory(): SaleRecord[] {
    const term = this.ticketSearchTerm.trim().toLowerCase();
    if (!term) return this.allSales;
    return this.allSales.filter((sale) => {
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

  setReportPanel(panel: ReportPanel): void {
    this.reportPanel = panel;
    this.activeSections = { ...this.activeSections, reports: panel };
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
      reports: {
        summary: 'Resumen',
        sales: 'Ventas',
        tickets: 'Tickets',
        refunds: 'Devoluciones',
        movements: 'Movimientos',
      },
      settings: {
        profile: 'Configuracion',
      },
    };
    return labels[view][section] ?? '';
  }

  changeReportDate(date: string): void {
    this.reportDate = date || this.todayDateString();
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
        trend: 'Seleccionado',
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
          this.loginError =
            error instanceof Error && error.name === 'TimeoutError'
              ? 'El backend no respondio. Revisa que este corriendo en http://localhost:8080'
              : 'No pude conectar con el backend local. Revisa http://localhost:8080';
        },
      });
  }

  logout(): void {
    this.loggedIn = false;
    this.sessionToken = '';
    this.loginUser = '';
    this.loginPass = '';
    this.loginError = '';
    this.cart = [];
    this.selectedCustomerId = null;
    this.checkoutDiscount = 0;
    this.cashReceived = 0;
    this.lastTicket = null;
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
    if (view !== 'pos') this.searchTerm = '';
  }

  newSale(): void {
    this.cart = [];
    this.selectedCustomerId = null;
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
    this.statusMessage = `Cliente: ${
      customerId
        ? (this.customers.find((customer) => customer.id === customerId)?.name ?? 'Mostrador')
        : 'Mostrador'
    }`;
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
          this.checkoutDiscount = 0;
          this.cashReceived = 0;
          this.lastTicket = sale;
          this.activeSections = { ...this.activeSections, pos: 'ticket' };
          this.openTicketPdf(sale);
          this.statusMessage =
            sale.status === 'PENDING'
              ? `Pago con ${this.paymentLabel(sale.paymentMethod)} pendiente de confirmar. PDF abierto.`
              : 'Venta cobrada. PDF abierto para imprimir.';
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
              logoUrl: this.settingsForm.logoUrl,
              thankYouMessage: this.settingsForm.thankYouMessage,
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
    this.refresh
      .track(
        'Generando respaldo...',
        this.http.get<Record<string, unknown>>(this.apiUrl('/backup'), this.authOptions()),
      )
      .subscribe({
        next: (backup) => {
          const payload = this.buildBackupWorkbook(backup);
          const blob = new Blob([payload], { type: 'application/vnd.ms-excel;charset=utf-8' });
          const url = URL.createObjectURL(blob);
          const link = document.createElement('a');
          link.href = url;
          link.download = `boutique-os-respaldo-${this.todayDateString()}.xls`;
          link.click();
          URL.revokeObjectURL(url);
          this.settingsMessage = 'Backup formal descargado en Excel';
        },
        error: () => {
          this.settingsMessage = 'No se pudo generar el backup';
          this.statusMessage = this.settingsMessage;
        },
      });
  }

  saveCashCount(): void {
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
          this.actualCashInput = cashCount.actualCash;
          this.cashCountNotes = cashCount.notes || '';
          this.cashCountUpdatedAt = cashCount.updatedAt;
          this.statusMessage = 'Corte de efectivo guardado';
          this.isSavingCashCount = false;
        },
        error: () => {
          this.statusMessage = 'No se pudo guardar el efectivo real';
          this.isSavingCashCount = false;
        },
      });
  }

  openLastTicketPdf(): void {
    if (!this.lastTicket) {
      this.statusMessage = 'No hay ticket reciente para imprimir';
      return;
    }
    this.openTicketPdf(this.lastTicket);
    this.statusMessage = 'PDF del ticket abierto';
  }

  openSaleTicketPdf(sale: SaleRecord): void {
    this.openTicketPdf(sale);
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

  productStatusLabel(status: ProductStatus): string {
    return this.productStatuses.find((item) => item.value === status)?.label ?? status;
  }

  inventoryMovementLabel(type: InventoryMovementType): string {
    if (type === 'PURCHASE') return 'Compra';
    if (type === 'SALE') return 'Venta';
    if (type === 'RETURN') return 'Devolucion';
    return 'Ajuste';
  }

  private openTicketPdf(sale: SaleRecord): void {
    const doc = new jsPDF({
      orientation: 'portrait',
      unit: 'mm',
      format: [80, 220],
    });

    const pageWidth = 80;
    const margin = 6;
    const contentWidth = pageWidth - margin * 2;
    let y = 10;

    const center = (text: string, size = 10, style: 'normal' | 'bold' = 'normal') => {
      doc.setFont('helvetica', style);
      doc.setFontSize(size);
      doc.text(text, pageWidth / 2, y, { align: 'center' });
      y += size * 0.55 + 2;
    };

    const line = () => {
      doc.setDrawColor(190);
      doc.line(margin, y, pageWidth - margin, y);
      y += 4;
    };

    const row = (label: string, value: string, bold = false) => {
      doc.setFont('helvetica', bold ? 'bold' : 'normal');
      doc.setFontSize(8.5);
      doc.text(label, margin, y);
      doc.text(value, pageWidth - margin, y, { align: 'right' });
      y += 5;
    };

    const wrap = (text: string) => doc.splitTextToSize(text || '', contentWidth) as string[];

    if (this.settings.logoUrl.startsWith('data:image/')) {
      try {
        doc.addImage(this.settings.logoUrl, 'PNG', 28, y, 24, 16, undefined, 'FAST');
        y += 19;
      } catch {
        y += 1;
      }
    }

    center(this.settings.storeName || 'Boutique OS', 12, 'bold');
    if (this.settings.address) center(this.settings.address, 7);
    if (!this.settings.address && this.settings.street) center(this.settings.street, 7);
    if (!this.settings.address && this.settings.neighborhood) center(this.settings.neighborhood, 7);
    if (!this.settings.address && (this.settings.city || this.settings.postalCode)) {
      center(
        `${this.settings.city}${this.settings.city && this.settings.postalCode ? ' CP ' : ''}${this.settings.postalCode}`,
        7,
      );
    }
    if (this.settings.phone) center(this.settings.phone, 7);
    center('Ticket de venta', 9);
    y += 1;
    row('Venta', `#${sale.id}`, true);
    row('Fecha', this.formatDateTime(sale.createdAt));
    if (sale.refundedAt) {
      row('Devuelta', this.formatDateTime(sale.refundedAt));
    }
    row('Cliente', sale.customerName || 'Mostrador');
    row('Pago', this.paymentLabel(sale.paymentMethod));
    row('Estado', this.saleStatusLabel(sale.status));
    line();

    for (const item of sale.items) {
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
    row('Descuento', `-${this.formatMoney(sale.discount)}`);
    if (sale.refundedTotal > 0) {
      row('Devuelto', `-${this.formatMoney(sale.refundedTotal)}`);
    }
    row('Total', this.formatMoney(sale.total), true);
    if (sale.paymentMethod === 'CASH') {
      row('Recibido', this.formatMoney(sale.cashReceived || 0));
      row('Cambio', this.formatMoney(sale.changeDue || 0), true);
    }
    y += 4;
    center(this.settings.thankYouMessage || 'Gracias por tu compra', 8, 'bold');

    const blobUrl = doc.output('bloburl');
    const printWindow = window.open(blobUrl, '_blank', 'noopener,noreferrer');

    if (!printWindow) {
      doc.save(`ticket-venta-${sale.id}.pdf`);
      this.statusMessage = 'No pude abrir la ventana. Descargue el PDF del ticket.';
    }
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
    const sections = [
      ['Configuracion', [backup['settings']]],
      ['Productos', backup['products']],
      ['Categorias', backup['productCategories']],
      ['Clientes', backup['customers']],
      ['Ventas', backup['sales']],
      ['Devoluciones', backup['saleRefunds']],
      ['Compras', backup['purchases']],
      ['Movimientos de inventario', backup['inventoryMovements']],
      ['Cortes de caja', backup['dailyCashCounts']],
    ] as const;

    const generatedAt = this.escapeHtml(String(backup['generatedAt'] || new Date().toISOString()));
    const body = sections
      .map(([title, data]) => this.backupSection(title, Array.isArray(data) ? data : []))
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
      logoUrl: settings.logoUrl || '',
      address: settings.address || '',
      thankYouMessage: settings.thankYouMessage || 'Gracias por tu compra',
    };
    this.credentialsForm = {
      username: settings.username || 'admin',
      currentPassword: '',
      newPassword: '',
    };
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
      });
  }

  private loadCashCount(): void {
    this.http
      .get<DailyCashCount>(this.apiUrl(`/reports/cash-count/today?date=${this.reportDate}`))
      .subscribe({
        next: (cashCount) => {
          this.actualCashInput = cashCount.actualCash;
          this.cashCountNotes = cashCount.notes || '';
          this.cashCountUpdatedAt = cashCount.updatedAt;
        },
      });
  }

  private refreshReportData(): void {
    this.loadSalesToday();
    this.loadAllSales();
    this.loadRefundsToday();
    this.loadCashCount();
    this.loadReportInventoryMovements();
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

  private viewLabel(view: ViewId): string {
    const labels: Record<ViewId, string> = {
      pos: 'punto de venta',
      products: 'productos',
      catalog: 'catalogo',
      categories: 'categorias',
      inventory: 'inventario',
      customers: 'clientes',
      reports: 'corte diario',
      settings: 'configuracion',
    };
    return labels[view];
  }

  private showAlert(message: string): void {
    if (!message || message === 'Listo para vender') return;
    this.alertMessage = message;
    this.alertType = this.inferAlertType(message);
    if (this.alertTimer) {
      clearTimeout(this.alertTimer);
    }
    this.alertTimer = setTimeout(() => {
      this.alertMessage = '';
      this.alertTimer = null;
    }, 3200);
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

  private toDateInputValue(date: Date): string {
    const offset = date.getTimezoneOffset();
    return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 10);
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
