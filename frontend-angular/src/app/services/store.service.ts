import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { jsPDF } from 'jspdf';

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
export type ReportPanel = 'summary' | 'sales' | 'tickets' | 'refunds';
export type InventoryPanel = 'summary' | 'purchases' | 'movements';

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

export type ViewId = 'pos' | 'products' | 'inventory' | 'customers' | 'reports';

@Injectable({ providedIn: 'root' })
export class StoreService {
  loggedIn = false;
  loginUser = '';
  loginPass = '';
  loginError = '';

  activeView: ViewId = 'pos';
  products: Product[] = [];
  cart: CartItem[] = [];
  salesToday: SaleRecord[] = [];
  allSales: SaleRecord[] = [];
  refundsToday: SaleRefundRecord[] = [];
  pendingSales: SaleRecord[] = [];
  reportDate = this.todayDateString();
  reportPanel: ReportPanel = 'summary';
  selectedPayment: PaymentMethod = 'CASH';
  statusMessage = 'Listo para vender';
  isCharging = false;
  searchTerm = '';
  customers: Customer[] = [];
  inventoryMovements: InventoryMovement[] = [];
  recentPurchases: PurchaseRecord[] = [];
  inventoryDate = this.todayDateString();
  inventoryPanel: InventoryPanel = 'summary';
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
  editingProductId: number | null = null;
  purchaseForm = {
    productId: null as number | null,
    supplierName: '',
    quantity: 1,
    unitCost: 0,
    note: ''
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
    status: 'ACTIVE' as ProductStatus
  };
  productImageFileName = '';

  readonly paymentMethods = [
    { label: 'Efectivo', value: 'CASH' as PaymentMethod },
    { label: 'Transferencia', value: 'TRANSFER' as PaymentMethod },
    { label: 'Tarjeta', value: 'CARD' as PaymentMethod }
  ];

  readonly productStatuses = [
    { label: 'Activo', value: 'ACTIVE' as ProductStatus },
    { label: 'Agotado', value: 'OUT_OF_STOCK' as ProductStatus },
    { label: 'Archivado', value: 'ARCHIVED' as ProductStatus }
  ];

  readonly navItems = [
    { id: 'pos' as ViewId, label: 'Punto de venta', icon: '🛒' },
    { id: 'products' as ViewId, label: 'Productos', icon: '📦' },
    { id: 'inventory' as ViewId, label: 'Inventario', icon: '📋' },
    { id: 'customers' as ViewId, label: 'Clientes', icon: '👥' },
    { id: 'reports' as ViewId, label: 'Corte diario', icon: '📊' }
  ];

  readonly reportPanels = [
    { id: 'summary' as ReportPanel, label: 'Resumen' },
    { id: 'sales' as ReportPanel, label: 'Ventas' },
    { id: 'tickets' as ReportPanel, label: 'Tickets' },
    { id: 'refunds' as ReportPanel, label: 'Devoluciones' }
  ];

  readonly inventoryPanels = [
    { id: 'summary' as InventoryPanel, label: 'Resumen' },
    { id: 'purchases' as InventoryPanel, label: 'Compras' },
    { id: 'movements' as InventoryPanel, label: 'Movimientos' }
  ];

  constructor(private readonly http: HttpClient) {}

  get filteredProducts(): Product[] {
    if (!this.searchTerm.trim()) return this.products;
    const q = this.searchTerm.toLowerCase();
    return this.products.filter(
      (p) =>
        p.name.toLowerCase().includes(q) ||
        p.category.toLowerCase().includes(q) ||
        (p.sku && p.sku.toLowerCase().includes(q))
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
      .filter((sale) => sale.status !== 'PENDING' && sale.status !== 'CANCELLED' && sale.paymentMethod === 'CASH')
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
      adjustments: this.inventoryMovements.filter((movement) => movement.type === 'ADJUSTMENT').length,
      returns: this.inventoryMovements.filter((movement) => movement.type === 'RETURN').length
    };
  }

  get paymentSummary() {
    return this.paymentMethods.map((method) => {
      const sales = this.salesToday.filter(
        (sale) => sale.status !== 'PENDING' && sale.status !== 'CANCELLED' && sale.paymentMethod === method.value
      );
      const refunds = this.refundedToday.filter((sale) => sale.paymentMethod === method.value);
      const total = sales.reduce((sum, sale) => sum + sale.total, 0) - refunds.reduce((sum, sale) => sum + sale.total, 0);
      return { ...method, total, count: sales.length, refunds: refunds.length };
    });
  }

  get topProductsToday() {
    const counts = new Map<string, number>();
    for (const sale of this.salesToday.filter((item) => item.status !== 'PENDING' && item.status !== 'CANCELLED')) {
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
      const productText = sale.items.map((item) => item.productName).join(' ').toLowerCase();
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
  }

  setInventoryPanel(panel: InventoryPanel): void {
    this.inventoryPanel = panel;
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
    return (sale.status === 'CONFIRMED' || sale.status === 'PARTIALLY_REFUNDED') &&
      sale.items.some((item) => this.refundableQuantity(item) > 0);
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
        [item.id]: safeValue
      }
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
      inventory: 'Inventario',
      customers: 'Clientes',
      reports: 'Corte diario'
    };
    return titles[this.activeView];
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
      { label: 'Venta actual', value: this.formatMoney(this.cartTotal), trend: `${this.cart.length} partidas` },
      {
        label: 'Productos activos',
        value: String(this.products.filter((product) => product.status !== 'ARCHIVED').length),
        trend: 'Catalogo'
      },
      {
        label: 'Pendientes',
        value: String(this.pendingSales.length),
        trend: this.pendingSales.length ? 'Por confirmar' : 'Sin pendientes'
      },
      { label: 'Metodo pago', value: this.paymentLabel(this.selectedPayment), trend: 'Seleccionado' }
    ];
  }

  login(): void {
    if (this.loginUser === 'admin' && this.loginPass === 'admin') {
      this.loggedIn = true;
      this.loginError = '';
      this.loadProducts();
      this.loadSalesToday();
      this.loadCustomers();
      this.loadPendingSales();
      this.refreshReportData();
    } else {
      this.loginError = 'Usuario o contraseña incorrectos';
    }
  }

  logout(): void {
    this.loggedIn = false;
    this.loginUser = '';
    this.loginPass = '';
    this.loginError = '';
    this.cart = [];
    this.selectedCustomerId = null;
    this.checkoutDiscount = 0;
    this.lastTicket = null;
  }

  setView(view: ViewId): void {
    this.activeView = view;
    if (view === 'reports' || view === 'pos') this.loadSalesToday();
    if (view === 'reports') {
      this.refreshReportData();
    }
    if (view === 'inventory') {
      this.refreshInventoryData();
    }
    if (view === 'customers') this.loadCustomers();
    if (view !== 'pos') this.searchTerm = '';
  }

  newSale(): void {
    this.cart = [];
    this.selectedCustomerId = null;
    this.checkoutDiscount = 0;
    this.lastTicket = null;
    this.statusMessage = 'Listo para vender';
    this.setView('pos');
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
      this.cart = [...this.cart, { productId: product.id, name: product.name, qty: 1, price: product.salePrice }];
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
    this.http
      .post<SaleRecord>('http://localhost:8080/api/sales', {
        paymentMethod: this.selectedPayment,
        discount: this.cartDiscount,
        customerId: this.selectedCustomerId,
        items: this.cart.map((item) => ({ productId: item.productId, quantity: item.qty }))
      })
      .subscribe({
        next: (sale) => {
          this.cart = [];
          this.selectedCustomerId = null;
          this.checkoutDiscount = 0;
          this.lastTicket = sale;
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
        }
      });
  }

  confirmSale(id: number): void {
    this.http.post<SaleRecord>(`http://localhost:8080/api/sales/${id}/confirm`, {}).subscribe({
      next: (sale) => {
        this.statusMessage = 'Pago confirmado';
        this.lastTicket = sale;
        this.loadSalesToday();
        this.loadPendingSales();
        this.refreshReportData();
      },
      error: () => {
        this.statusMessage = 'No se pudo confirmar el pago';
      }
    });
  }

  cancelSale(id: number): void {
    this.http.post<SaleRecord>(`http://localhost:8080/api/sales/${id}/cancel`, {}).subscribe({
      next: () => {
        this.statusMessage = 'Venta pendiente cancelada y stock repuesto';
        this.loadProducts();
        this.loadSalesToday();
        this.loadPendingSales();
        this.refreshReportData();
      },
      error: () => {
        this.statusMessage = 'No se pudo cancelar la venta';
      }
    });
  }

  refundSale(id: number): void {
    const draft = this.refundDrafts[id] ?? {};
    const items = Object.entries(draft)
      .map(([saleItemId, quantity]) => ({ saleItemId: Number(saleItemId), quantity }))
      .filter((item) => item.quantity > 0);

    this.http.post<SaleRecord>(`http://localhost:8080/api/sales/${id}/refund`, items.length ? { items } : null).subscribe({
      next: (sale) => {
        this.statusMessage = sale.status === 'REFUNDED'
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
      }
    });
  }

  createProduct(): void {
    if (!this.productForm.name.trim()) {
      this.statusMessage = 'El producto necesita nombre';
      return;
    }
    const payload = {
      ...this.productForm,
      imageUrl: this.productForm.imageUrl || null
    };

    const request = this.editingProductId
      ? this.http.put<Product>(`http://localhost:8080/api/products/${this.editingProductId}`, payload)
      : this.http.post<Product>('http://localhost:8080/api/products', payload);

    request.subscribe({
      next: () => {
        this.statusMessage = this.editingProductId ? 'Producto actualizado' : 'Producto creado';
        this.resetProductForm();
        this.loadProducts();
      },
      error: () => {
        this.statusMessage = this.editingProductId ? 'No se pudo actualizar el producto' : 'No se pudo crear el producto';
      }
    });
  }

  editProduct(product: Product): void {
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
      status: product.status
    };
    this.productImageFileName = product.imageUrl ? 'Imagen cargada' : '';
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

  cancelProductEdit(): void {
    this.resetProductForm();
  }

  deleteProduct(product: Product): void {
    this.http.delete(`http://localhost:8080/api/products/${product.id}`).subscribe({
      next: () => {
        this.statusMessage = `${product.name} eliminado`;
        if (this.editingProductId === product.id) {
          this.resetProductForm();
        }
        this.loadProducts();
      },
      error: () => {
        this.statusMessage = 'No se pudo eliminar el producto';
      }
    });
  }

  restock(product: Product, quantity: number): void {
    this.adjustInventory(product.id, quantity, `Ajuste manual para ${product.name}`, 'Stock actualizado');
  }

  reduceStock(product: Product): void {
    this.adjustInventory(product.id, -1, `Ajuste manual para ${product.name}`, 'Ajuste de inventario guardado');
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

    this.http
      .post<PurchaseRecord>('http://localhost:8080/api/purchases', {
        productId: this.purchaseForm.productId,
        supplierName: this.purchaseForm.supplierName || null,
        quantity: this.purchaseForm.quantity,
        unitCost: this.purchaseForm.unitCost,
        note: this.purchaseForm.note || null
      })
      .subscribe({
        next: () => {
          this.statusMessage = 'Compra registrada y stock actualizado';
          this.purchaseForm = { productId: null, supplierName: '', quantity: 1, unitCost: 0, note: '' };
          this.loadProducts();
          this.refreshInventoryData();
        },
        error: () => {
          this.statusMessage = 'No se pudo registrar la compra';
        }
      });
  }

  addCustomer(): void {
    if (!this.newCustomerName.trim()) {
      this.statusMessage = 'El cliente necesita nombre';
      return;
    }
    this.http
      .post<Customer>('http://localhost:8080/api/customers', {
        name: this.newCustomerName,
        phone: this.newCustomerPhone || null,
        notes: this.newCustomerNotes || null
      })
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
        }
      });
  }

  deleteCustomer(customer: Customer): void {
    this.http.delete(`http://localhost:8080/api/customers/${customer.id}`).subscribe({
      next: () => {
        this.statusMessage = `${customer.name} eliminado`;
        if (this.selectedCustomerId === customer.id) this.selectedCustomerId = null;
        if (this.selectedCustomerHistory?.id === customer.id) this.selectedCustomerHistory = null;
        this.loadCustomers();
      },
      error: () => {
        this.statusMessage = 'No se pudo eliminar el cliente';
      }
    });
  }

  showCustomerSales(customer: Customer): void {
    this.selectedCustomerHistory = customer;
    this.http.get<SaleRecord[]>(`http://localhost:8080/api/sales/customer/${customer.id}`).subscribe({
      next: (sales) => {
        this.customerSales = sales;
      },
      error: () => {
        this.customerSales = [];
      }
    });
  }

  clearCustomerHistory(): void {
    this.selectedCustomerHistory = null;
    this.customerSales = [];
  }

  clearTicket(): void {
    this.lastTicket = null;
  }

  saveCashCount(): void {
    this.isSavingCashCount = true;
    this.http.put<DailyCashCount>(`http://localhost:8080/api/reports/cash-count/today?date=${this.reportDate}`, {
      actualCash: this.actualCashInput,
      notes: this.cashCountNotes || null
    }).subscribe({
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
      }
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
      minute: '2-digit'
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
      format: [80, 180]
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

    const wrap = (text: string) =>
      doc.splitTextToSize(text || '', contentWidth) as string[];

    center('BOUTIQUE', 12, 'bold');
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
      const detail = item.refundedQuantity > 0
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
    y += 4;
    center('Gracias por tu compra', 8, 'bold');

    const blobUrl = doc.output('bloburl');
    const printWindow = window.open(blobUrl, '_blank', 'noopener,noreferrer');

    if (!printWindow) {
      doc.save(`ticket-venta-${sale.id}.pdf`);
      this.statusMessage = 'No pude abrir la ventana. Descargue el PDF del ticket.';
    }
  }

  private loadProducts(): void {
    this.http.get<Product[]>('http://localhost:8080/api/products').subscribe({
      next: (products) => {
        this.products = products;
      },
      error: () => {
        this.statusMessage = 'No pude cargar productos del backend';
      }
    });
  }

  private loadSalesToday(): void {
    this.http.get<SaleRecord[]>(`http://localhost:8080/api/sales/today?date=${this.reportDate}`).subscribe({
      next: (sales) => {
        this.salesToday = sales;
      },
      error: () => {
        this.statusMessage = 'No pude cargar el corte del dia';
      }
    });
  }

  private loadCustomers(): void {
    this.http.get<Customer[]>('http://localhost:8080/api/customers').subscribe({
      next: (customers) => {
        this.customers = customers;
        if (this.selectedCustomerId && !customers.find((customer) => customer.id === this.selectedCustomerId)) {
          this.selectedCustomerId = null;
        }
      },
      error: () => {
        this.statusMessage = 'No pude cargar clientes del backend';
      }
    });
  }

  private loadPendingSales(): void {
    this.http.get<SaleRecord[]>('http://localhost:8080/api/sales/pending').subscribe({
      next: (sales) => {
        this.pendingSales = sales;
      }
    });
  }

  private loadAllSales(): void {
    this.http.get<SaleRecord[]>(`http://localhost:8080/api/sales?date=${this.reportDate}`).subscribe({
      next: (sales) => {
        this.allSales = sales;
      }
    });
  }

  private loadRefundsToday(): void {
    this.http.get<SaleRefundRecord[]>(`http://localhost:8080/api/sales/refunds/today?date=${this.reportDate}`).subscribe({
      next: (refunds) => {
        this.refundsToday = refunds;
      }
    });
  }

  private loadCashCount(): void {
    this.http.get<DailyCashCount>(`http://localhost:8080/api/reports/cash-count/today?date=${this.reportDate}`).subscribe({
      next: (cashCount) => {
        this.actualCashInput = cashCount.actualCash;
        this.cashCountNotes = cashCount.notes || '';
        this.cashCountUpdatedAt = cashCount.updatedAt;
      }
    });
  }

  private refreshReportData(): void {
    this.loadSalesToday();
    this.loadAllSales();
    this.loadRefundsToday();
    this.loadCashCount();
  }

  private loadInventoryMovements(): void {
    this.http.get<InventoryMovement[]>(`http://localhost:8080/api/inventory/movements?date=${this.inventoryDate}`).subscribe({
      next: (movements) => {
        this.inventoryMovements = movements;
      }
    });
  }

  private loadPurchases(): void {
    this.http.get<PurchaseRecord[]>(`http://localhost:8080/api/purchases?date=${this.inventoryDate}`).subscribe({
      next: (purchases) => {
        this.recentPurchases = purchases;
      }
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
    message: string
  ): void {
    this.http
      .post<Product>('http://localhost:8080/api/inventory/adjustments', {
        productId,
        quantityDelta,
        note
      })
      .subscribe({
        next: () => {
          this.statusMessage = message;
          this.loadProducts();
          this.loadInventoryMovements();
        },
        error: () => {
          this.statusMessage = 'No se pudo actualizar inventario';
        }
      });
  }

  private quantityInCart(productId: number): number {
    return this.cart.find((item) => item.productId === productId)?.qty ?? 0;
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
      status: 'ACTIVE'
    };
    this.productImageFileName = '';
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
}
