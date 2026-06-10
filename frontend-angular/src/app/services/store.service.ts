import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export type PaymentMethod = 'CASH' | 'TRANSFER' | 'CARD';
export type SaleStatus = 'PENDING' | 'CONFIRMED';

export interface Product {
  id: number;
  name: string;
  category: string;
  size: string;
  color: string;
  sku: string;
  costPrice: number;
  salePrice: number;
  stock: number;
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

export interface SaleRecord {
  id: number;
  total: number;
  estimatedProfit: number;
  paymentMethod: PaymentMethod;
  status: SaleStatus;
  customerId: number | null;
  customerName: string | null;
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
  pendingSales: SaleRecord[] = [];
  selectedPayment: PaymentMethod = 'CASH';
  statusMessage = 'Listo para vender';
  isCharging = false;
  searchTerm = '';
  customers: Customer[] = [];
  selectedCustomerId: number | null = null;
  newCustomerName = '';
  newCustomerPhone = '';
  newCustomerNotes = '';
  selectedCustomerHistory: Customer | null = null;
  customerSales: SaleRecord[] = [];
  productForm = { name: '', category: '', size: '', color: '', sku: '', costPrice: 0, salePrice: 0, stock: 0 };

  readonly paymentMethods = [
    { label: 'Efectivo', value: 'CASH' as PaymentMethod },
    { label: 'Transferencia', value: 'TRANSFER' as PaymentMethod },
    { label: 'Tarjeta', value: 'CARD' as PaymentMethod }
  ];

  readonly navItems = [
    { id: 'pos' as ViewId, label: 'Punto de venta', icon: '🛒' },
    { id: 'products' as ViewId, label: 'Productos', icon: '📦' },
    { id: 'inventory' as ViewId, label: 'Inventario', icon: '📋' },
    { id: 'customers' as ViewId, label: 'Clientes', icon: '👥' },
    { id: 'reports' as ViewId, label: 'Corte diario', icon: '📊' }
  ];

  constructor(private readonly http: HttpClient) {}

  get filteredProducts(): Product[] {
    if (!this.searchTerm.trim()) return this.products;
    const q = this.searchTerm.toLowerCase();
    return this.products.filter(
      (p) => p.name.toLowerCase().includes(q) || p.category.toLowerCase().includes(q) || (p.sku && p.sku.toLowerCase().includes(q))
    );
  }

  get cartTotal(): number {
    return this.cart.reduce((total, item) => total + item.qty * item.price, 0);
  }

  get todayTotal(): number {
    return this.salesToday.reduce((total, sale) => total + sale.total, 0);
  }

  get todayProfit(): number {
    return this.salesToday.reduce((total, sale) => total + sale.estimatedProfit, 0);
  }

  get pageTitle(): string {
    const titles: Record<ViewId, string> = { pos: 'Punto de venta', products: 'Productos', inventory: 'Inventario', customers: 'Clientes', reports: 'Corte diario' };
    return titles[this.activeView];
  }

  get tasks(): string[] {
    const t: string[] = [];
    if (this.pendingSales.length > 0) t.push(`${this.pendingSales.length} pago(s) pendiente(s) por confirmar`);
    for (const p of this.products.filter((p) => p.stock <= 2)) t.push(`Reponer ${p.name} (${p.stock} uds)`);
    if (t.length === 0) t.push('Sin novedades');
    return t;
  }

  get stats() {
    return [
      { label: 'Venta actual', value: this.formatMoney(this.cartTotal), trend: `${this.cart.length} partidas` },
      { label: 'Productos activos', value: String(this.products.length), trend: 'Catalogo' },
      { label: 'Pendientes', value: String(this.pendingSales.length), trend: this.pendingSales.length ? 'Por confirmar' : 'Sin pendientes' },
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
  }

  setView(view: ViewId): void {
    this.activeView = view;
    if (view === 'reports' || view === 'pos') this.loadSalesToday();
    if (view === 'customers') this.loadCustomers();
    if (view !== 'pos') this.searchTerm = '';
  }

  newSale(): void {
    this.cart = [];
    this.selectedCustomerId = null;
    this.statusMessage = 'Listo para vender';
    this.setView('pos');
  }

  addToCart(product: Product): void {
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
    this.cart = this.cart.map((item) => item.productId === productId ? { ...item, qty: item.qty - 1 } : item).filter((item) => item.qty > 0);
    this.statusMessage = 'Carrito actualizado';
  }

  clearCart(): void {
    this.cart = [];
    this.statusMessage = 'Venta limpiada';
  }

  selectPayment(method: PaymentMethod): void {
    this.selectedPayment = method;
    this.statusMessage = `Metodo seleccionado: ${this.paymentLabel(method)}`;
  }

  selectCustomer(customerId: number | null): void {
    this.selectedCustomerId = customerId;
    this.statusMessage = `Cliente: ${customerId ? (this.customers.find((c) => c.id === customerId)?.name ?? 'Mostrador') : 'Mostrador'}`;
  }

  checkout(): void {
    if (!this.cart.length || this.isCharging) { this.statusMessage = 'Agrega productos antes de cobrar'; return; }
    this.isCharging = true;
    this.http.post<SaleRecord>('http://localhost:8080/api/sales', {
      paymentMethod: this.selectedPayment, discount: 0, customerId: this.selectedCustomerId,
      items: this.cart.map((item) => ({ productId: item.productId, quantity: item.qty }))
    }).subscribe({
      next: (sale) => {
        this.cart = []; this.selectedCustomerId = null;
        this.statusMessage = sale.status === 'PENDING' ? `Pago con ${this.paymentLabel(sale.paymentMethod)} pendiente de confirmar` : 'Venta cobrada';
        this.isCharging = false; this.loadProducts(); this.loadSalesToday(); this.loadPendingSales();
      },
      error: () => { this.statusMessage = 'No se pudo cobrar. Revisa backend o stock.'; this.isCharging = false; }
    });
  }

  confirmSale(id: number): void {
    this.http.post<SaleRecord>(`http://localhost:8080/api/sales/${id}/confirm`, {}).subscribe({
      next: () => { this.statusMessage = 'Pago confirmado'; this.loadSalesToday(); this.loadPendingSales(); },
      error: () => { this.statusMessage = 'No se pudo confirmar el pago'; }
    });
  }

  createProduct(): void {
    if (!this.productForm.name.trim()) { this.statusMessage = 'El producto necesita nombre'; return; }
    this.http.post<Product>('http://localhost:8080/api/products', { ...this.productForm, status: 'ACTIVE' }).subscribe({
      next: () => {
        this.statusMessage = 'Producto creado';
        this.productForm = { name: '', category: '', size: '', color: '', sku: '', costPrice: 0, salePrice: 0, stock: 0 };
        this.loadProducts();
      },
      error: () => { this.statusMessage = 'No se pudo crear el producto'; }
    });
  }

  restock(product: Product, quantity: number): void { this.updateProductStock(product, product.stock + quantity, 'Stock actualizado'); }
  reduceStock(product: Product): void { this.updateProductStock(product, Math.max(product.stock - 1, 0), 'Ajuste de inventario guardado'); }

  addCustomer(): void {
    if (!this.newCustomerName.trim()) { this.statusMessage = 'El cliente necesita nombre'; return; }
    this.http.post<Customer>('http://localhost:8080/api/customers', { name: this.newCustomerName, phone: this.newCustomerPhone || null, notes: this.newCustomerNotes || null }).subscribe({
      next: () => { this.statusMessage = 'Cliente agregado'; this.newCustomerName = ''; this.newCustomerPhone = ''; this.newCustomerNotes = ''; this.loadCustomers(); },
      error: () => { this.statusMessage = 'No se pudo agregar el cliente'; }
    });
  }

  deleteCustomer(customer: Customer): void {
    this.http.delete(`http://localhost:8080/api/customers/${customer.id}`).subscribe({
      next: () => { this.statusMessage = `${customer.name} eliminado`; if (this.selectedCustomerId === customer.id) this.selectedCustomerId = null; if (this.selectedCustomerHistory?.id === customer.id) this.selectedCustomerHistory = null; this.loadCustomers(); },
      error: () => { this.statusMessage = 'No se pudo eliminar el cliente'; }
    });
  }

  showCustomerSales(customer: Customer): void {
    this.selectedCustomerHistory = customer;
    this.http.get<SaleRecord[]>('http://localhost:8080/api/sales/today').subscribe({
      next: (sales) => { this.customerSales = sales.filter((s) => s.customerId === customer.id); },
      error: () => { this.customerSales = []; }
    });
  }

  clearCustomerHistory(): void { this.selectedCustomerHistory = null; this.customerSales = []; }

  formatMoney(value: number): string { return `$${value.toLocaleString('es-MX')}`; }
  paymentLabel(method: PaymentMethod): string { return this.paymentMethods.find((item) => item.value === method)?.label ?? method; }

  private loadProducts(): void {
    this.http.get<Product[]>('http://localhost:8080/api/products').subscribe({
      next: (products) => { this.products = products; },
      error: () => { this.statusMessage = 'No pude cargar productos del backend'; }
    });
  }

  private loadSalesToday(): void {
    this.http.get<SaleRecord[]>('http://localhost:8080/api/sales/today').subscribe({
      next: (sales) => { this.salesToday = sales; },
      error: () => { this.statusMessage = 'No pude cargar el corte del dia'; }
    });
  }

  private loadCustomers(): void {
    this.http.get<Customer[]>('http://localhost:8080/api/customers').subscribe({
      next: (customers) => { this.customers = customers; if (this.selectedCustomerId && !customers.find((c) => c.id === this.selectedCustomerId)) this.selectedCustomerId = null; },
      error: () => { this.statusMessage = 'No pude cargar clientes del backend'; }
    });
  }

  private loadPendingSales(): void {
    this.http.get<SaleRecord[]>('http://localhost:8080/api/sales/pending').subscribe({
      next: (sales) => { this.pendingSales = sales; }
    });
  }

  private updateProductStock(product: Product, stock: number, message: string): void {
    this.http.put<Product>(`http://localhost:8080/api/products/${product.id}`, { ...product, stock }).subscribe({
      next: () => { this.statusMessage = message; this.loadProducts(); },
      error: () => { this.statusMessage = 'No se pudo actualizar inventario'; }
    });
  }

  private quantityInCart(productId: number): number {
    return this.cart.find((item) => item.productId === productId)?.qty ?? 0;
  }
}
