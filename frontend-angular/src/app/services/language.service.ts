import { Injectable, signal } from '@angular/core';

export type AppLang = 'es' | 'en';

export type TranslateParams = Record<string, string | number>;

export interface TranslationEntry {
  es: string;
  en: string;
}

const STORAGE_KEY = 'boutiqueos.lang';

export const TRANSLATIONS: Record<string, TranslationEntry> = {
  // ----- common -----
  'common.save': { es: 'Guardar', en: 'Save' },
  'common.saving': { es: 'Guardando...', en: 'Saving...' },
  'common.cancel': { es: 'Cancelar', en: 'Cancel' },
  'common.close': { es: 'Cerrar', en: 'Close' },
  'common.edit': { es: 'Editar', en: 'Edit' },
  'common.delete': { es: 'Eliminar', en: 'Delete' },
  'common.update': { es: 'Actualizar', en: 'Update' },
  'common.search': { es: 'Buscar', en: 'Search' },
  'common.all': { es: 'Todo', en: 'All' },
  'common.optional': { es: 'Opcional', en: 'Optional' },
  'common.today': { es: 'Hoy', en: 'Today' },
  'common.yesterday': { es: 'Ayer', en: 'Yesterday' },
  'common.tomorrow': { es: 'Mañana', en: 'Tomorrow' },
  'common.units': { es: 'uds', en: 'pcs' },
  'common.backToLogin': { es: 'Volver al login', en: 'Back to login' },
  'common.other': { es: 'Otro', en: 'Other' },

  // ----- nav / views -----
  'nav.pos': { es: 'Punto de venta', en: 'Point of sale' },
  'nav.products': { es: 'Productos', en: 'Products' },
  'nav.catalog': { es: 'Catalogo', en: 'Catalog' },
  'nav.categories': { es: 'Categorias', en: 'Categories' },
  'nav.inventory': { es: 'Inventario', en: 'Inventory' },
  'nav.customers': { es: 'Clientes', en: 'Customers' },
  'nav.promos': { es: 'Promos', en: 'Promotions' },
  'nav.reports': { es: 'Corte diario', en: 'Daily report' },
  'nav.settings': { es: 'Configuracion', en: 'Settings' },

  'view.pos': { es: 'punto de venta', en: 'point of sale' },
  'view.products': { es: 'productos', en: 'products' },
  'view.catalog': { es: 'catalogo', en: 'catalog' },
  'view.categories': { es: 'categorias', en: 'categories' },
  'view.inventory': { es: 'inventario', en: 'inventory' },
  'view.customers': { es: 'clientes', en: 'customers' },
  'view.promos': { es: 'promos', en: 'promotions' },
  'view.reports': { es: 'corte diario', en: 'daily report' },
  'view.settings': { es: 'configuracion', en: 'settings' },

  // ----- language toggle -----
  'lang.label': { es: 'Idioma', en: 'Language' },

  // ----- section titles -----
  'section.pos.products': { es: 'Productos', en: 'Products' },
  'section.pos.sale': { es: 'Venta actual', en: 'Current sale' },
  'section.pos.ticket': { es: 'Ticket listo', en: 'Receipt ready' },
  'section.products.form': { es: 'Alta de producto', en: 'New product' },
  'section.products.catalog': { es: 'Catalogo', en: 'Catalog' },
  'section.catalog.products': { es: 'Productos', en: 'Products' },
  'section.categories.categories': { es: 'Categorias', en: 'Categories' },
  'section.inventory.summary': { es: 'Resumen', en: 'Summary' },
  'section.inventory.purchases': { es: 'Compras', en: 'Purchases' },
  'section.customers.form': { es: 'Nuevo cliente', en: 'New customer' },
  'section.customers.list': { es: 'Listado', en: 'Listing' },
  'section.customers.history': { es: 'Historial', en: 'History' },
  'section.promos.form': { es: 'Nueva promo', en: 'New promo' },
  'section.promos.list': { es: 'Listado', en: 'Listing' },
  'section.reports.summary': { es: 'Resumen', en: 'Summary' },
  'section.reports.sales': { es: 'Ventas', en: 'Sales' },
  'section.reports.tickets': { es: 'Tickets', en: 'Receipts' },
  'section.reports.refunds': { es: 'Devoluciones', en: 'Refunds' },
  'section.reports.movements': { es: 'Movimientos', en: 'Movements' },
  'section.reports.history': { es: 'Historial', en: 'History' },
  'section.settings.profile': { es: 'Configuracion', en: 'Settings' },

  // ----- module strip -----
  'module.title': { es: 'Modulos', en: 'Modules' },
  'module.caption': { es: 'Accesos directos del sistema', en: 'Quick access to the system' },
  'module.aria': { es: 'Modulos principales', en: 'Main modules' },

  // ----- refresh overlay -----
  'refresh.updating': { es: 'Actualizando...', en: 'Updating...' },
  'refresh.moment': { es: 'Un momento', en: 'One moment' },
  'refresh.loadingView': { es: 'Cargando {view}...', en: 'Loading {view}...' },
  'refresh.processingSale': { es: 'Procesando venta...', en: 'Processing sale...' },
  'refresh.confirmingPayment': { es: 'Confirmando pago...', en: 'Confirming payment...' },
  'refresh.cancellingSale': { es: 'Cancelando venta...', en: 'Cancelling sale...' },
  'refresh.processingRefund': { es: 'Procesando devolucion...', en: 'Processing refund...' },
  'refresh.updatingProduct': { es: 'Actualizando producto...', en: 'Updating product...' },
  'refresh.savingProduct': { es: 'Guardando producto...', en: 'Saving product...' },
  'refresh.updatingCategory': { es: 'Actualizando categoria...', en: 'Updating category...' },
  'refresh.savingCategory': { es: 'Guardando categoria...', en: 'Saving category...' },
  'refresh.deletingCategory': { es: 'Eliminando categoria...', en: 'Deleting category...' },
  'refresh.deletingProduct': { es: 'Eliminando producto...', en: 'Deleting product...' },
  'refresh.registeringPurchase': { es: 'Registrando compra...', en: 'Registering purchase...' },
  'refresh.updatingPurchase': { es: 'Actualizando compra...', en: 'Updating purchase...' },
  'refresh.deletingPurchase': { es: 'Eliminando compra...', en: 'Deleting purchase...' },
  'refresh.deletingMovement': { es: 'Eliminando movimiento...', en: 'Deleting movement...' },
  'refresh.updatingCustomer': { es: 'Actualizando cliente...', en: 'Updating customer...' },
  'refresh.savingCustomer': { es: 'Guardando cliente...', en: 'Saving customer...' },
  'refresh.deletingCustomer': { es: 'Eliminando cliente...', en: 'Deleting customer...' },
  'refresh.loadingHistory': { es: 'Cargando historial...', en: 'Loading history...' },
  'refresh.savingTicketData': { es: 'Guardando datos del ticket...', en: 'Saving receipt data...' },
  'refresh.savingAccess': { es: 'Guardando acceso...', en: 'Saving access...' },
  'refresh.savingCashCount': { es: 'Guardando corte...', en: 'Saving cash count...' },
  'refresh.closingDay': { es: 'Cerrando dia...', en: 'Closing day...' },
  'refresh.reopeningDay': { es: 'Reabriendo dia...', en: 'Reopening day...' },
  'refresh.generatingExcel': {
    es: 'Generando respaldo Excel...',
    en: 'Generating Excel backup...',
  },
  'refresh.generatingCsv': { es: 'Generando respaldo CSV...', en: 'Generating CSV backup...' },
  'refresh.generatingPdf': { es: 'Generando respaldo PDF...', en: 'Generating PDF backup...' },
  'refresh.signingIn': { es: 'Iniciando sesion...', en: 'Signing in...' },
  'refresh.sendingLink': { es: 'Enviando enlace...', en: 'Sending link...' },
  'refresh.updatingPassword': { es: 'Actualizando contraseña...', en: 'Updating password...' },
  'refresh.updatingInventory': { es: 'Actualizando inventario...', en: 'Updating inventory...' },

  // ----- topbar -----
  'topbar.newSale': { es: 'Nueva venta', en: 'New sale' },
  'topbar.admin': { es: 'Admin', en: 'Admin' },
  'topbar.logout': { es: 'Salir', en: 'Log out' },

  // ----- login -----
  'login.user': { es: 'Usuario', en: 'User' },
  'login.password': { es: 'Contraseña', en: 'Password' },
  'login.connecting': { es: 'Conectando', en: 'Connecting' },
  'login.enter': { es: 'Entrar', en: 'Sign in' },
  'login.forgot': { es: '¿Olvidaste la contraseña?', en: 'Forgot your password?' },

  // ----- onboarding -----
  'onboarding.title': { es: 'Activa tu empresa', en: 'Activate your business' },
  'onboarding.subtitle': {
    es: 'Pago confirmado. Completa tu alta segura para entrar al sistema.',
    en: 'Payment confirmed. Complete your secure signup to enter the system.',
  },
  'onboarding.retry': { es: 'Reintentar validacion', en: 'Retry validation' },
  'onboarding.checking': {
    es: 'Estamos confirmando tu pago con Stripe. Esto normalmente tarda menos de un segundo.',
    en: "We're confirming your payment with Stripe. This usually takes less than a second.",
  },
  'onboarding.storeName': { es: 'Nombre de la empresa', en: 'Business name' },
  'onboarding.phone': { es: 'Telefono', en: 'Phone' },
  'onboarding.email': { es: 'Correo de acceso', en: 'Access email' },
  'onboarding.street': { es: 'Calle y numero', en: 'Street and number' },
  'onboarding.neighborhood': { es: 'Colonia', en: 'Neighborhood' },
  'onboarding.city': { es: 'Ciudad', en: 'City' },
  'onboarding.postalCode': { es: 'Codigo postal', en: 'Postal code' },
  'onboarding.password': { es: 'Contraseña', en: 'Password' },
  'onboarding.confirmPassword': { es: 'Confirmar contraseña', en: 'Confirm password' },
  'onboarding.activating': { es: 'Activando', en: 'Activating' },
  'onboarding.create': { es: 'Crear cuenta y continuar', en: 'Create account and continue' },

  // ----- recovery -----
  'recovery.title': { es: 'Restablece tu contraseña', en: 'Reset your password' },
  'recovery.copyRequest': {
    es: 'Ingresa tu correo o usuario y te enviaremos un enlace para restablecer tu contraseña.',
    en: "Enter your email or username and we'll send you a link to reset your password.",
  },
  'recovery.userLabel': { es: 'Correo o usuario', en: 'Email or username' },
  'recovery.sending': { es: 'Enviando', en: 'Sending' },
  'recovery.sendLink': { es: 'Enviar enlace', en: 'Send link' },
  'recovery.hintEmail': {
    es: 'Estás recuperando la cuenta vinculada a {email}.',
    en: "You're recovering the account linked to {email}.",
  },
  'recovery.checking': {
    es: 'Estamos validando tu enlace de recuperación...',
    en: "We're validating your recovery link...",
  },
  'recovery.newPassword': { es: 'Nueva contraseña', en: 'New password' },
  'recovery.confirmPassword': { es: 'Confirmar contraseña', en: 'Confirm password' },
  'recovery.passwordHint': {
    es: 'La contraseña debe llevar mínimo 8 caracteres, una mayúscula, una minúscula y un número.',
    en: 'The password must have at least 8 characters, one uppercase, one lowercase and one number.',
  },
  'recovery.saving': { es: 'Guardando', en: 'Saving' },
  'recovery.savePassword': { es: 'Guardar nueva contraseña', en: 'Save new password' },

  // ----- pos -----
  'pos.products': { es: 'Productos', en: 'Products' },
  'pos.searchPlaceholder': {
    es: 'Buscar nombre, categoria o SKU...',
    en: 'Search name, category or SKU...',
  },
  'pos.noMatch': { es: 'No hay productos que coincidan', en: 'No matching products' },
  'pos.sale': { es: 'Venta', en: 'Sale' },
  'pos.clear': { es: 'Limpiar', en: 'Clear' },
  'pos.customer': { es: 'Cliente', en: 'Customer' },
  'pos.counter': { es: 'Mostrador', en: 'Walk-in' },
  'pos.addProducts': { es: 'Agrega productos', en: 'Add products' },
  'pos.paymentMethod': { es: 'Metodo de pago', en: 'Payment method' },
  'pos.discount': { es: 'Descuento', en: 'Discount' },
  'pos.promo': { es: 'Promo', en: 'Promo' },
  'pos.noPromo': { es: 'Sin promo', en: 'No promo' },
  'pos.promoApplied': {
    es: '{name} aplicado con {value}',
    en: '{name} applied with {value}',
  },
  'pos.promoNoLonger': {
    es: '{name} ya no aplica a esta venta',
    en: '{name} no longer applies to this sale',
  },
  'pos.received': { es: 'Recibido', en: 'Received' },
  'pos.change': { es: 'Cambio', en: 'Change' },
  'pos.pieces': { es: 'Piezas', en: 'Pieces' },
  'pos.subtotal': { es: 'Subtotal', en: 'Subtotal' },
  'pos.total': { es: 'Total', en: 'Total' },
  'pos.charging': { es: 'Cobrando...', en: 'Charging...' },
  'pos.charge': { es: 'Cobrar', en: 'Charge' },
  'pos.ticketReady': { es: 'Ticket listo', en: 'Receipt ready' },
  'pos.customerColon': { es: 'Cliente:', en: 'Customer:' },
  'pos.totalColon': { es: 'Total:', en: 'Total:' },
  'pos.paymentColon': { es: 'Pago:', en: 'Payment:' },
  'pos.changeColon': { es: 'Cambio:', en: 'Change:' },
  'pos.pdfHint': {
    es: 'El PDF se abre al cobrar. Si no apareció, puedes volver a abrirlo aquí.',
    en: "The PDF opens when you charge. If it didn't show up, you can open it again here.",
  },
  'pos.openPdf': { es: 'Abrir PDF', en: 'Open PDF' },
  'pos.hide': { es: 'Ocultar', en: 'Hide' },
  'pos.method': { es: 'Metodo', en: 'Method' },
  'pos.status': { es: 'Estado', en: 'Status' },

  // ----- products -----
  'products.title': { es: 'Catalogo de productos', en: 'Product catalog' },
  'products.subtitle': {
    es: 'Consulta, edita y abre el formulario solo cuando lo necesites',
    en: 'Browse, edit and open the form only when you need it',
  },
  'products.count': { es: '{n} productos', en: '{n} products' },
  'products.another': { es: 'Otro producto', en: 'Another product' },
  'products.new': { es: 'Nuevo producto', en: 'New product' },
  'products.edit': { es: 'Editar producto', en: 'Edit product' },
  'products.capture': { es: 'Capturar producto', en: 'Capture product' },
  'products.name': { es: 'Nombre', en: 'Name' },
  'products.category': { es: 'Categoria', en: 'Category' },
  'products.selectCategory': { es: 'Selecciona una categoria', en: 'Select a category' },
  'products.categoryHint': {
    es: 'Elige una categoria y el sistema ajusta el campo de talla automaticamente.',
    en: 'Choose a category and the system adjusts the size field automatically.',
  },
  'products.size': { es: 'Talla', en: 'Size' },
  'products.color': { es: 'Color', en: 'Color' },
  'products.colorPlaceholder': { es: 'Azul, Negro...', en: 'Blue, Black...' },
  'products.image': { es: 'Imagen', en: 'Image' },
  'products.chooseImage': { es: 'Elegir imagen', en: 'Choose image' },
  'products.remove': { es: 'Quitar', en: 'Remove' },
  'products.noImage': { es: 'Sin imagen seleccionada', en: 'No image selected' },
  'products.cost': { es: 'Costo ($)', en: 'Cost ($)' },
  'products.price': { es: 'Precio ($)', en: 'Price ($)' },
  'products.stock': { es: 'Stock', en: 'Stock' },
  'products.sku': { es: 'SKU', en: 'SKU' },
  'products.status': { es: 'Estado', en: 'Status' },
  'products.withoutCategory': { es: 'Sin categoria', en: 'No category' },
  'products.withoutSize': { es: 'Sin talla', en: 'No size' },
  'products.newByType': { es: 'Nuevos por tipo:', en: 'New by type:' },
  'products.role': { es: 'Talla', en: 'Size' },
  'products.noProducts': { es: 'Sin productos registrados', en: 'No products registered' },
  'products.imageLoaded': { es: 'Imagen cargada', en: 'Image loaded' },

  // ----- confirm dialogs -----
  'confirm.deleteCategory': { es: 'Eliminar categoria {name}?', en: 'Delete category {name}?' },
  'confirm.deletePromo': { es: 'Eliminar promo {name}?', en: 'Delete promo {name}?' },
  'confirm.deletePurchase': { es: 'Eliminar compra de {name}?', en: 'Delete purchase of {name}?' },
  'confirm.deleteMovement': {
    es: 'Eliminar movimiento de {name}?',
    en: 'Delete movement of {name}?',
  },

  // ----- categories -----
  'categories.edit': { es: 'Editar categoria', en: 'Edit category' },
  'categories.new': { es: 'Nueva categoria', en: 'New category' },
  'categories.baseType': { es: 'Tipo base', en: 'Base type' },
  'categories.name': { es: 'Nombre', en: 'Name' },
  'categories.description': { es: 'Descripcion', en: 'Description' },
  'categories.descriptionPlaceholder': {
    es: 'Descripcion de la categoria',
    en: 'Category description',
  },
  'categories.active': { es: 'Activa', en: 'Active' },
  'categories.yes': { es: 'Si', en: 'Yes' },
  'categories.no': { es: 'No', en: 'No' },
  'categories.title': { es: 'Categorias', en: 'Categories' },
  'categories.count': { es: '{n} categorias', en: '{n} categories' },
  'categories.productsLinked': { es: '{n} producto(s) ligados', en: '{n} linked product(s)' },
  'categories.activeBadge': { es: 'Activa', en: 'Active' },
  'categories.inactiveBadge': { es: 'Inactiva', en: 'Inactive' },
  'categories.sizeField': { es: 'Campo de talla:', en: 'Size field:' },
  'categories.sizeDefault': { es: 'Talla', en: 'Size' },
  'categories.noDescription': { es: 'Sin descripcion', en: 'No description' },
  'categories.linkedStock': { es: 'Stock ligado:', en: 'Linked stock:' },
  'categories.valueAtSale': { es: 'Valor a precio venta:', en: 'Value at sale price:' },
  'categories.useInProduct': { es: 'Usar en producto', en: 'Use in product' },
  'categories.saveType': { es: 'Guardar tipo', en: 'Save type' },
  'categories.noCategories': { es: 'Sin categorias registradas', en: 'No categories registered' },

  // ----- inventory -----
  'inventory.title': { es: 'Inventario', en: 'Inventory' },
  'inventory.subtitle': {
    es: 'Selecciona la fecha y revisa stock actual o compras del dia',
    en: 'Select the date and review current stock or day purchases',
  },
  'inventory.date': { es: 'Fecha', en: 'Date' },
  'inventory.currentStock': { es: 'Stock actual', en: 'Current stock' },
  'inventory.todayPurchases': { es: 'Compras del dia', en: 'Day purchases' },
  'inventory.totalPurchased': { es: 'Total comprado', en: 'Total purchased' },
  'inventory.movements': { es: 'Movimientos', en: 'Movements' },
  'inventory.adjustments': { es: 'Ajustes', en: 'Adjustments' },
  'inventory.purchases': { es: 'Compras', en: 'Purchases' },
  'inventory.outflows': { es: 'Salidas', en: 'Outflows' },
  'inventory.returns': { es: 'Devoluciones', en: 'Returns' },
  'inventory.registerPurchase': { es: 'Registrar compra', en: 'Register purchase' },
  'inventory.editPurchase': { es: 'Editar compra', en: 'Edit purchase' },
  'inventory.product': { es: 'Producto', en: 'Product' },
  'inventory.selectProduct': { es: 'Selecciona producto', en: 'Select product' },
  'inventory.supplier': { es: 'Proveedor', en: 'Supplier' },
  'inventory.quantity': { es: 'Cantidad', en: 'Quantity' },
  'inventory.unitCost': { es: 'Costo unitario', en: 'Unit cost' },
  'inventory.note': { es: 'Nota', en: 'Note' },
  'inventory.notePlaceholder': {
    es: 'Compra de reposicion, nueva coleccion...',
    en: 'Restock purchase, new collection...',
  },
  'inventory.savePurchase': { es: 'Guardar compra', en: 'Save purchase' },
  'inventory.purchasesOf': { es: 'Compras del {date}', en: 'Purchases of {date}' },
  'inventory.noSupplier': { es: 'Sin proveedor', en: 'No supplier' },
  'inventory.noNote': { es: 'Sin nota', en: 'No note' },
  'inventory.noPurchasesDate': {
    es: 'Sin compras registradas para esta fecha',
    en: 'No purchases registered for this date',
  },
  'inventory.noProducts': { es: 'Sin productos cargados', en: 'No products loaded' },
  'inventory.action': { es: 'Accion', en: 'Action' },

  // ----- customers -----
  'customers.new': { es: 'Nuevo cliente', en: 'New customer' },
  'customers.edit': { es: 'Editar cliente', en: 'Edit customer' },
  'customers.name': { es: 'Nombre', en: 'Name' },
  'customers.whatsapp': { es: 'WhatsApp', en: 'WhatsApp' },
  'customers.notes': { es: 'Notas', en: 'Notes' },
  'customers.relationship': { es: 'Relacion', en: 'Relationship' },
  'customers.confirmedSales': { es: 'Compras cerradas', en: 'Confirmed purchases' },
  'customers.totalConfirmed': { es: 'Total confirmado', en: 'Confirmed total' },
  'customers.averageTicket': { es: 'Ticket promedio', en: 'Average ticket' },
  'customers.pending': { es: 'Pendientes', en: 'Pending' },
  'customers.preferredMethod': { es: 'Metodo preferido', en: 'Preferred method' },
  'customers.lastPurchase': { es: 'Ultima compra', en: 'Last purchase' },
  'customers.noRecord': { es: 'Sin registro', en: 'No record' },
  'customers.internalNotes': { es: 'Notas internas', en: 'Internal notes' },
  'customers.noNotesRegistered': { es: 'Sin notas registradas', en: 'No notes registered' },
  'customers.buysMost': { es: 'Lo que mas compra', en: 'What they buy most' },
  'customers.noPatterns': {
    es: 'Todavia no hay patrones de compra para este cliente.',
    en: 'There are still no purchase patterns for this customer.',
  },
  'customers.noPurchases': { es: 'Sin compras registradas', en: 'No purchases registered' },
  'customers.title': { es: 'Clientes', en: 'Customers' },
  'customers.noWhatsapp': { es: 'Sin WhatsApp', en: 'No WhatsApp' },
  'customers.pieces': { es: 'pzas', en: 'pcs' },
  'customers.searchPlaceholder': { es: 'Nombre, WhatsApp o nota', en: 'Name, WhatsApp or note' },
  'customers.noRelationshipNotes': {
    es: 'Sin notas de relacion registradas.',
    en: 'No relationship notes registered.',
  },
  'customers.since': { es: 'Alta:', en: 'Since:' },
  'customers.openChat': { es: 'Abrir chat', en: 'Open chat' },
  'customers.viewRelationship': { es: 'Ver relacion', en: 'View relationship' },
  'customers.noCustomers': {
    es: 'Sin clientes registrados o sin coincidencias para esa busqueda.',
    en: 'No customers registered or no matches for that search.',
  },
  'customers.date': { es: 'Fecha', en: 'Date' },
  'customers.method': { es: 'Metodo', en: 'Method' },
  'customers.status': { es: 'Estado', en: 'Status' },
  'customers.total': { es: 'Total', en: 'Total' },

  // ----- promos -----
  'promos.edit': { es: 'Editar promo', en: 'Edit promo' },
  'promos.new': { es: 'Nueva promo', en: 'New promo' },
  'promos.name': { es: 'Nombre', en: 'Name' },
  'promos.code': { es: 'Codigo', en: 'Code' },
  'promos.type': { es: 'Tipo', en: 'Type' },
  'promos.value': { es: 'Valor', en: 'Value' },
  'promos.minPurchase': { es: 'Compra minima', en: 'Minimum purchase' },
  'promos.customer': { es: 'Cliente', en: 'Customer' },
  'promos.all': { es: 'Todos', en: 'All' },
  'promos.starts': { es: 'Inicia', en: 'Starts' },
  'promos.ends': { es: 'Termina', en: 'Ends' },
  'promos.notes': { es: 'Notas', en: 'Notes' },
  'promos.notesPlaceholder': {
    es: 'Ej. mover inventario lento o premiar cliente VIP',
    en: 'E.g. move slow inventory or reward VIP customer',
  },
  'promos.searchPlaceholder': { es: 'Nombre, codigo o cliente', en: 'Name, code or customer' },
  'promos.activeCheckbox': { es: 'Promo activa', en: 'Active promo' },
  'promos.update': { es: 'Actualizar promo', en: 'Update promo' },
  'promos.save': { es: 'Guardar promo', en: 'Save promo' },
  'promos.title': { es: 'Promos', en: 'Promotions' },
  'promos.minimum': { es: 'Minimo:', en: 'Minimum:' },
  'promos.noNotes': { es: 'Sin notas internas.', en: 'No internal notes.' },
  'promos.noPromos': { es: 'Todavia no hay promos guardadas.', en: 'No promos saved yet.' },

  // ----- reports shell -----
  'reports.daily': { es: 'Corte diario', en: 'Daily report' },
  'reports.subtitle': {
    es: 'Selecciona la fecha y luego el panel que quieres revisar',
    en: 'Select the date and then the panel you want to review',
  },
  'reports.date': { es: 'Fecha', en: 'Date' },
  'reports.exportPdf': { es: 'Exportar PDF', en: 'Export PDF' },
  'reports.summary': { es: 'Resumen', en: 'Summary' },
  'reports.sales': { es: 'Ventas', en: 'Sales' },
  'reports.tickets': { es: 'Tickets', en: 'Receipts' },
  'reports.refunds': { es: 'Devoluciones', en: 'Refunds' },
  'reports.movements': { es: 'Movimientos', en: 'Movements' },
  'reports.history': { es: 'Historial', en: 'History' },

  // ----- report summary -----
  'summary.of': { es: 'Resumen del {date}', en: 'Summary of {date}' },
  'summary.dayClosed': { es: 'Dia cerrado', en: 'Day closed' },
  'summary.closedAt': { es: 'Cerrado: {date}', en: 'Closed: {date}' },
  'summary.noDate': { es: 'Sin fecha', en: 'No date' },
  'summary.locked': { es: 'Bloqueado para edicion', en: 'Locked for editing' },
  'summary.netSold': { es: 'Vendido neto', en: 'Net sales' },
  'summary.ticketsCharged': { es: '{n} ticket(s) cobrados', en: '{n} receipt(s) charged' },
  'summary.netProfit': { es: 'Utilidad neta', en: 'Net profit' },
  'summary.margin': { es: 'Margen {n}%', en: 'Margin {n}%' },
  'summary.expectedBox': { es: 'Caja total esperada', en: 'Expected total box' },
  'summary.expectedCash': { es: 'Efectivo esperado:', en: 'Expected cash:' },
  'summary.difference': { es: 'Diferencia', en: 'Difference' },
  'summary.pendingCount': { es: 'Pendientes: {n}', en: 'Pending: {n}' },
  'summary.vsYesterday': { es: 'vs ayer', en: 'vs yesterday' },
  'summary.yesterdayColon': { es: 'Ayer:', en: 'Yesterday:' },
  'summary.countSales': { es: '{n} venta(s)', en: '{n} sale(s)' },
  'summary.refundsShort': { es: '{n} devol.', en: '{n} ref.' },
  'summary.averageTicketShort': { es: 'Ticket prom.:', en: 'Avg ticket:' },
  'summary.expectedCashPill': { es: 'Efectivo esperado', en: 'Expected cash' },
  'summary.incidentFilters': { es: 'Filtros rapidos de incidencias', en: 'Quick incident filters' },
  'summary.clearFilter': { es: 'Limpiar filtro', en: 'Clear filter' },
  'summary.dayMetrics': { es: 'Metricas del dia', en: 'Day metrics' },
  'summary.averageTicket': { es: 'Ticket promedio', en: 'Average ticket' },
  'summary.piecesSold': { es: 'Piezas vendidas', en: 'Pieces sold' },
  'summary.peakHour': { es: 'Hora pico', en: 'Peak hour' },
  'summary.topProduct': { es: 'Top producto', en: 'Top product' },
  'summary.noSales': { es: 'Sin ventas', en: 'No sales' },
  'summary.alerts': { es: 'Alertas', en: 'Alerts' },
  'summary.cashCut': { es: 'Corte de efectivo', en: 'Cash count' },
  'summary.expected': { es: 'Esperado', en: 'Expected' },
  'summary.actualCash': { es: 'Efectivo real en caja', en: 'Actual cash in box' },
  'summary.cashNotes': { es: 'Notas del corte', en: 'Cut notes' },
  'summary.cashNotesPlaceholder': {
    es: 'Opcional: faltante, cambio, retiro...',
    en: 'Optional: missing, change, withdrawal...',
  },
  'summary.savedAt': { es: 'Guardado: {date}', en: 'Saved: {date}' },
  'summary.closingAt': { es: 'Cierre: {date}', en: 'Closing: {date}' },
  'summary.incidents': { es: 'Incidencias', en: 'Incidents' },
  'summary.incidentsHint': {
    es: 'Devoluciones, pendientes y cancelaciones del dia',
    en: 'Refunds, pending and cancellations of the day',
  },
  'summary.saveCut': { es: 'Guardar corte', en: 'Save cut' },
  'summary.closing': { es: 'Cerrando...', en: 'Closing...' },
  'summary.closeDay': { es: 'Cerrar dia', en: 'Close day' },
  'summary.reopening': { es: 'Reabriendo...', en: 'Reopening...' },
  'summary.reopenDay': { es: 'Reabrir dia', en: 'Reopen day' },
  'summary.topProducts': { es: 'Productos mas vendidos', en: 'Best-selling products' },
  'summary.noConfirmedSales': {
    es: 'Sin ventas confirmadas para esta fecha',
    en: 'No confirmed sales for this date',
  },

  // ----- report sales -----
  'sales.title': { es: 'Ventas del {date}', en: 'Sales of {date}' },
  'sales.records': { es: '{n} registros', en: '{n} records' },
  'sales.filtering': { es: 'Filtrando:', en: 'Filtering:' },
  'sales.viewAll': { es: 'Ver todo', en: 'View all' },
  'sales.pending': { es: 'Pendiente', en: 'Pending' },
  'sales.partial': { es: 'Parcial', en: 'Partial' },
  'sales.cancelled': { es: 'Cancelada', en: 'Cancelled' },
  'sales.refunded': { es: 'Devuelta', en: 'Refunded' },
  'sales.confirmed': { es: 'Confirmada', en: 'Confirmed' },
  'sales.confirm': { es: 'Confirmar', en: 'Confirm' },
  'sales.cancel': { es: 'Cancelar', en: 'Cancel' },
  'sales.noSalesDate': {
    es: 'Sin ventas registradas para esta fecha',
    en: 'No sales registered for this date',
  },
  'sales.customer': { es: 'Cliente', en: 'Customer' },
  'sales.profit': { es: 'Ganancia', en: 'Profit' },
  'sales.total': { es: 'Total', en: 'Total' },

  // ----- report tickets -----
  'tickets.title': { es: 'Tickets del {date}', en: 'Receipts of {date}' },
  'tickets.searchPlaceholder': {
    es: 'Buscar ticket, cliente, estado o producto...',
    en: 'Search receipt, customer, status or product...',
  },
  'tickets.refundedAt': { es: 'Devuelta: {date}', en: 'Refunded: {date}' },
  'tickets.refundedTotal': { es: 'Devuelto acumulado: {total}', en: 'Accumulated refund: {total}' },
  'tickets.refund': { es: 'Devolver', en: 'Refund' },
  'tickets.ready': {
    es: 'Listo para devolver: {n} unidad(es)',
    en: 'Ready to refund: {n} unit(s)',
  },
  'tickets.available': { es: '{name} · disp. {n}', en: '{name} · avail. {n}' },
  'tickets.noTickets': {
    es: 'No hay tickets para la fecha seleccionada',
    en: 'No receipts for the selected date',
  },
  'tickets.ticket': { es: 'Ticket', en: 'Receipt' },
  'tickets.actions': { es: 'Acciones', en: 'Actions' },

  // ----- report refunds -----
  'refunds.title': { es: 'Devoluciones del {date}', en: 'Refunds of {date}' },
  'refunds.records': { es: '{n} registros', en: '{n} records' },
  'refunds.noRefunds': {
    es: 'Sin devoluciones para la fecha seleccionada',
    en: 'No refunds for the selected date',
  },
  'refunds.folio': { es: 'Folio', en: 'No.' },

  // ----- report movements -----
  'movements.title': {
    es: 'Movimientos del inventario del {date}',
    en: 'Inventory movements of {date}',
  },
  'movements.simpleTitle': { es: 'Movimientos del {date}', en: 'Movements of {date}' },
  'movements.noMovements': {
    es: 'Sin movimientos para la fecha seleccionada',
    en: 'No movements for the selected date',
  },
  'movements.date': { es: 'Fecha', en: 'Date' },
  'movements.product': { es: 'Producto', en: 'Product' },
  'movements.type': { es: 'Tipo', en: 'Type' },
  'movements.quantity': { es: 'Cantidad', en: 'Quantity' },
  'movements.cost': { es: 'Costo', en: 'Cost' },
  'movements.note': { es: 'Nota', en: 'Note' },

  // ----- report history -----
  'history.closings': { es: 'Cierres', en: 'Closings' },
  'history.daysClosed': { es: 'Dias cerrados registrados', en: 'Registered closed days' },
  'history.open': { es: 'Abiertos', en: 'Open' },
  'history.daysOpen': {
    es: 'Dias aun editables en historial',
    en: 'Days still editable in history',
  },
  'history.lastClosing': { es: 'Ultimo cierre', en: 'Last closing' },
  'history.noClosing': { es: 'Sin cierre', en: 'No closing' },
  'history.lastClosingHint': {
    es: 'Registro mas reciente con cierre formal',
    en: 'Most recent record with formal closing',
  },
  'history.currentSelection': { es: 'Seleccion actual', en: 'Current selection' },
  'history.currentHint': { es: 'Dia activo en el modulo', en: 'Active day in the module' },
  'history.title': { es: 'Historial de cortes diarios', en: 'Daily cuts history' },
  'history.count': { es: '{n} registro(s)', en: '{n} record(s)' },
  'history.closed': { es: 'Cerrado', en: 'Closed' },
  'history.openBadge': { es: 'Abierto', en: 'Open' },
  'history.closedAt': { es: 'Cerrado: {date}', en: 'Closed: {date}' },
  'history.noNotes': { es: 'Sin notas', en: 'No notes' },
  'history.viewCut': { es: 'Ver corte', en: 'View cut' },
  'history.viewTickets': { es: 'Ver tickets', en: 'View receipts' },
  'history.noCuts': {
    es: 'Aun no hay cortes guardados en el historial',
    en: 'No cuts saved in history yet',
  },
  'history.status': { es: 'Estado', en: 'Status' },
  'history.actualCash': { es: 'Efectivo real', en: 'Actual cash' },
  'history.lastMovement': { es: 'Ultimo movimiento', en: 'Last movement' },
  'history.actions': { es: 'Acciones', en: 'Actions' },

  // ----- settings -----
  'settings.ticket': { es: 'Ticket', en: 'Receipt' },
  'settings.businessData': { es: 'Datos del negocio', en: 'Business data' },
  'settings.storeName': { es: 'Nombre de la tienda', en: 'Store name' },
  'settings.phone': { es: 'Telefono / WhatsApp', en: 'Phone / WhatsApp' },
  'settings.contactEmail': { es: 'Correo de contacto', en: 'Contact email' },
  'settings.socialNetwork': {
    es: 'Red social para el QR del ticket',
    en: 'Social network for receipt QR',
  },
  'settings.customLink': { es: 'Enlace propio', en: 'Custom link' },
  'settings.accountLink': { es: 'Cuenta o enlace', en: 'Account or link' },
  'settings.street': { es: 'Calle', en: 'Street' },
  'settings.streetNumber': { es: 'Calle y numero', en: 'Street and number' },
  'settings.neighborhood': { es: 'Colonia', en: 'Neighborhood' },
  'settings.city': { es: 'Ciudad o municipio', en: 'City or municipality' },
  'settings.cityShort': { es: 'Ciudad', en: 'City' },
  'settings.postalCode': { es: 'Codigo postal', en: 'Postal code' },
  'settings.branding': { es: 'Branding y mensaje', en: 'Branding and message' },
  'settings.ticketPrefix': { es: 'Prefijo del ticket', en: 'Receipt prefix' },
  'settings.paperFormat': { es: 'Formato de papel', en: 'Paper format' },
  'settings.thankYou': { es: 'Mensaje de agradecimiento', en: 'Thank-you message' },
  'settings.thankYouPlaceholder': {
    es: 'Gracias por tu compra',
    en: 'Thank you for your purchase',
  },
  'settings.footerNote': {
    es: 'Nota final / politica de cambios',
    en: 'Final note / exchange policy',
  },
  'settings.footerNotePlaceholder': {
    es: 'Cambios dentro de 15 dias con ticket',
    en: 'Exchanges within 15 days with receipt',
  },
  'settings.qrAlt': { es: 'QR de red social', en: 'Social network QR' },
  'settings.logo': { es: 'Logo', en: 'Logo' },
  'settings.uploadLogo': { es: 'Cargar logo', en: 'Upload logo' },
  'settings.logoUrlPlaceholder': { es: 'URL o imagen cargada', en: 'URL or uploaded image' },
  'settings.showOnTicket': { es: 'Mostrar en el ticket', en: 'Show on receipt' },
  'settings.logoToggle': { es: 'Logo', en: 'Logo' },
  'settings.addressToggle': { es: 'Direccion', en: 'Address' },
  'settings.phoneToggle': { es: 'Telefono', en: 'Phone' },
  'settings.customerToggle': { es: 'Cliente', en: 'Customer' },
  'settings.savingsToggle': { es: 'Ahorro / descuento', en: 'Savings / discount' },
  'settings.changeToggle': { es: 'Cambio', en: 'Change' },
  'settings.autoOpenTicket': {
    es: 'Abrir ticket automaticamente al cobrar',
    en: 'Open receipt automatically when charging',
  },
  'settings.preview': { es: 'Vista previa', en: 'Preview' },
  'settings.receiptTitle': { es: 'TICKET DE VENTA', en: 'SALES RECEIPT' },
  'settings.folio': { es: 'Folio', en: 'No.' },
  'settings.payment': { es: 'Pago', en: 'Payment' },
  'settings.cash': { es: 'Efectivo', en: 'Cash' },
  'settings.subtotal': { es: 'Subtotal', en: 'Subtotal' },
  'settings.savings': { es: 'Ahorro', en: 'Savings' },
  'settings.total': { es: 'Total', en: 'Total' },
  'settings.pieces': { es: 'Piezas', en: 'Pieces' },
  'settings.access': { es: 'Acceso', en: 'Access' },
  'settings.saveAccess': { es: 'Guardar acceso', en: 'Save access' },
  'settings.username': { es: 'Usuario', en: 'Username' },
  'settings.newPassword': { es: 'Nueva contrasena', en: 'New password' },
  'settings.newPasswordPlaceholder': {
    es: 'Dejar vacio para conservar',
    en: 'Leave empty to keep',
  },
  'settings.currentPassword': { es: 'Contrasena actual', en: 'Current password' },
  'settings.currentPasswordPlaceholder': {
    es: 'Obligatoria para guardar acceso',
    en: 'Required to save access',
  },
  'settings.warning': {
    es: 'Advertencia: por seguridad necesitas confirmar la contrasena actual antes de cambiar usuario o contrasena.',
    en: 'Warning: for security you must confirm the current password before changing username or password.',
  },
  'settings.backup': { es: 'Backup', en: 'Backup' },
  'settings.backupTitle': { es: 'Respaldo de datos', en: 'Data backup' },
  'settings.backupCopy': {
    es: 'Descarga productos, categorias, clientes, ventas, devoluciones, compras, movimientos, cortes y configuracion en Excel, CSV o PDF.',
    en: 'Download products, categories, customers, sales, refunds, purchases, movements, cuts and configuration in Excel, CSV or PDF.',
  },

  // ----- ticket PDF -----
  'ticket.title': { es: 'TICKET DE VENTA', en: 'SALES RECEIPT' },
  'ticket.folio': { es: 'Folio', en: 'No.' },
  'ticket.date': { es: 'Fecha', en: 'Date' },
  'ticket.refunded': { es: 'Devuelta', en: 'Refunded' },
  'ticket.customer': { es: 'Cliente', en: 'Customer' },
  'ticket.payment': { es: 'Pago', en: 'Payment' },
  'ticket.status': { es: 'Estado', en: 'Status' },
  'ticket.refundedQty': { es: '{n} dev.', en: '{n} ref.' },
  'ticket.subtotal': { es: 'Subtotal', en: 'Subtotal' },
  'ticket.savings': { es: 'Ahorro', en: 'Savings' },
  'ticket.refundedTotal': { es: 'Devuelto', en: 'Refunded' },
  'ticket.total': { es: 'Total', en: 'Total' },
  'ticket.received': { es: 'Recibido', en: 'Received' },
  'ticket.change': { es: 'Cambio', en: 'Change' },
  'ticket.pieces': { es: 'Piezas', en: 'Pieces' },
  'ticket.thankYou': { es: 'Gracias por tu compra', en: 'Thank you for your purchase' },

  // ----- daily report PDF -----
  'reportPdf.title': { es: 'Corte diario', en: 'Daily report' },
  'reportPdf.date': { es: 'Fecha:', en: 'Date:' },
  'reportPdf.generated': { es: 'Generado:', en: 'Generated:' },
  'reportPdf.executive': { es: 'Resumen ejecutivo', en: 'Executive summary' },
  'reportPdf.netSold': { es: 'Vendido neto', en: 'Net sales' },
  'reportPdf.netProfit': { es: 'Utilidad neta', en: 'Net profit' },
  'reportPdf.ticketsCharged': { es: 'Tickets cobrados', en: 'Receipts charged' },
  'reportPdf.ticketsPending': { es: 'Tickets pendientes', en: 'Pending receipts' },
  'reportPdf.expectedBox': { es: 'Caja total esperada', en: 'Expected total box' },
  'reportPdf.expectedCash': { es: 'Efectivo esperado', en: 'Expected cash' },
  'reportPdf.actualCash': { es: 'Efectivo real', en: 'Actual cash' },
  'reportPdf.difference': { es: 'Diferencia', en: 'Difference' },
  'reportPdf.comparison': { es: 'Comparacion vs ayer', en: 'Comparison vs yesterday' },
  'reportPdf.today': { es: 'hoy', en: 'today' },
  'reportPdf.yesterday': { es: 'ayer', en: 'yesterday' },
  'reportPdf.metrics': { es: 'Metricas', en: 'Metrics' },
  'reportPdf.averageTicket': { es: 'Ticket promedio', en: 'Average ticket' },
  'reportPdf.piecesSold': { es: 'Piezas vendidas', en: 'Pieces sold' },
  'reportPdf.averageMargin': { es: 'Margen promedio', en: 'Average margin' },
  'reportPdf.peakHour': { es: 'Hora pico', en: 'Peak hour' },
  'reportPdf.topProduct': { es: 'Top producto', en: 'Top product' },
  'reportPdf.byMethod': { es: 'Pago por metodo', en: 'Payment by method' },
  'reportPdf.alerts': { es: 'Alertas e incidencias', en: 'Alerts and incidents' },
  'reportPdf.cashCut': { es: 'Corte de caja', en: 'Cash count' },
  'reportPdf.currentDifference': { es: 'Diferencia actual', en: 'Current difference' },
  'reportPdf.dayStatus': { es: 'Estado del dia', en: 'Day status' },
  'reportPdf.closed': { es: 'Cerrado', en: 'Closed' },
  'reportPdf.open': { es: 'Abierto', en: 'Open' },
  'reportPdf.closedAt': { es: 'Cerrado a las', en: 'Closed at' },
  'reportPdf.lastSaved': { es: 'Ultimo guardado', en: 'Last saved' },
  'reportPdf.notSaved': { es: 'Sin guardar', en: 'Not saved' },
  'reportPdf.notes': { es: 'Notas:', en: 'Notes:' },
  'reportPdf.topProducts': { es: 'Productos mas vendidos', en: 'Best-selling products' },
  'reportPdf.noConfirmedSales': { es: 'Sin ventas confirmadas', en: 'No confirmed sales' },
  'reportPdf.averageShort': { es: 'promedio {value}', en: 'average {value}' },
  'reportPdf.noNotes': { es: 'Sin notas registradas.', en: 'No notes registered.' },

  // ----- backup PDF -----
  'backupPdf.title': { es: 'Respaldo Boutique OS', en: 'Boutique OS Backup' },
  'backupPdf.noData': { es: 'Sin datos', en: 'No data' },
  'backupPdf.moreRecords': {
    es: '... {n} registro(s) mas en el origen. Usa CSV o Excel para el detalle completo.',
    en: '... {n} more record(s) in the source. Use CSV or Excel for the full detail.',
  },
  'backup.configuration': { es: 'Configuracion', en: 'Configuration' },
  'backup.products': { es: 'Productos', en: 'Products' },
  'backup.categories': { es: 'Categorias', en: 'Categories' },
  'backup.customers': { es: 'Clientes', en: 'Customers' },
  'backup.sales': { es: 'Ventas', en: 'Sales' },
  'backup.refunds': { es: 'Devoluciones', en: 'Refunds' },
  'backup.purchases': { es: 'Compras', en: 'Purchases' },
  'backup.inventoryMovements': { es: 'Movimientos de inventario', en: 'Inventory movements' },
  'backup.cashCounts': { es: 'Cortes de caja', en: 'Cash counts' },

  // ----- payment methods -----
  'payment.CASH': { es: 'Efectivo', en: 'Cash' },
  'payment.TRANSFER': { es: 'Transferencia', en: 'Transfer' },
  'payment.CARD': { es: 'Tarjeta', en: 'Card' },

  // ----- product statuses -----
  'productStatus.ACTIVE': { es: 'Activo', en: 'Active' },
  'productStatus.OUT_OF_STOCK': { es: 'Agotado', en: 'Out of stock' },
  'productStatus.ARCHIVED': { es: 'Archivado', en: 'Archived' },

  // ----- promo types -----
  'promoType.PERCENT': { es: 'Porcentaje', en: 'Percentage' },
  'promoType.FIXED': { es: 'Monto fijo', en: 'Fixed amount' },

  // ----- paper sizes -----
  'paper.THERMAL_58': { es: 'Termica 58 mm', en: 'Thermal 58 mm' },
  'paper.THERMAL_80': { es: 'Termica 80 mm', en: 'Thermal 80 mm' },
  'paper.HALF_LETTER': { es: 'Media carta', en: 'Half letter' },

  // ----- sale statuses -----
  'saleStatus.PENDING': { es: 'Pendiente', en: 'Pending' },
  'saleStatus.PARTIALLY_REFUNDED': { es: 'Parcialmente devuelta', en: 'Partially refunded' },
  'saleStatus.CANCELLED': { es: 'Cancelada', en: 'Cancelled' },
  'saleStatus.REFUNDED': { es: 'Devuelta', en: 'Refunded' },
  'saleStatus.CONFIRMED': { es: 'Confirmada', en: 'Confirmed' },

  // ----- inventory movement types -----
  'movement.PURCHASE': { es: 'Compra', en: 'Purchase' },
  'movement.SALE': { es: 'Venta', en: 'Sale' },
  'movement.RETURN': { es: 'Devolucion', en: 'Refund' },
  'movement.ADJUSTMENT': { es: 'Ajuste', en: 'Adjustment' },

  // ----- promotion status -----
  'promoStatus.INACTIVE': { es: 'Inactiva', en: 'Inactive' },
  'promoStatus.EXPIRED': { es: 'Vencida', en: 'Expired' },
  'promoStatus.SCHEDULED': { es: 'Programada', en: 'Scheduled' },
  'promoStatus.ACTIVE': { es: 'Activa', en: 'Active' },

  // ----- customer relationship -----
  'customerStage.VIP': { es: 'Cliente VIP', en: 'VIP customer' },
  'customerStage.FREQUENT': { es: 'Cliente frecuente', en: 'Frequent customer' },
  'customerStage.ACTIVE': { es: 'Cliente activo', en: 'Active customer' },
  'customerStage.NEW': { es: 'Cliente nuevo', en: 'New customer' },
  'customerRecency.NONE': { es: 'Aun sin compras cerradas', en: 'No closed purchases yet' },
  'customerRecency.TODAY': { es: 'Compro hoy', en: 'Bought today' },
  'customerRecency.YESTERDAY': { es: 'Compro ayer', en: 'Bought yesterday' },
  'customerRecency.DAYS': { es: 'Compro hace {n} dias', en: 'Bought {n} days ago' },
  'customerRecency.WEEKS': { es: 'Compro hace {n} semana(s)', en: 'Bought {n} week(s) ago' },
  'customerRecency.MONTHS': { es: 'Compro hace {n} mes(es)', en: 'Bought {n} month(s) ago' },
  'customer.NoPreference': { es: 'Sin preferencia', en: 'No preference' },

  // ----- promo scopes -----
  'promoScope.ANY': { es: 'Aplica a cualquier cliente', en: 'Applies to any customer' },
  'promoScope.ONLY': { es: 'Solo para {name}', en: 'Only for {name}' },
  'promoScope.SPECIFIC': { es: 'Cliente especifico', en: 'Specific customer' },
  'promoWindow.RANGE': { es: '{from} al {to}', en: '{from} to {to}' },
  'promoWindow.FROM': { es: 'Desde {from}', en: 'From {from}' },

  // ----- incident filters -----
  'incident.ALL': { es: 'Todos', en: 'All' },
  'incident.PENDING': { es: 'Pendientes', en: 'Pending' },
  'incident.REFUNDS': { es: 'Devoluciones', en: 'Refunds' },
  'incident.CANCELLED': { es: 'Canceladas', en: 'Cancelled' },
  'incident.ADJUSTMENTS': { es: 'Ajustes de inventario', en: 'Inventory adjustments' },

  // ----- report alerts -----
  'alert.pendingSales': { es: 'Ventas pendientes', en: 'Pending sales' },
  'alert.pendingSalesDetail': {
    es: '{n} ticket(s) siguen pendientes de confirmar.',
    en: '{n} receipt(s) are still pending confirmation.',
  },
  'alert.cashDifference': { es: 'Diferencia en caja', en: 'Cash difference' },
  'alert.cashDifferenceDetail': {
    es: 'La diferencia actual es de {amount}.',
    en: 'The current difference is {amount}.',
  },
  'alert.refunds': { es: 'Devoluciones registradas', en: 'Refunds registered' },
  'alert.refundsDetail': {
    es: '{n} devolucion(es) por {amount}.',
    en: '{n} refund(s) totaling {amount}.',
  },
  'alert.inventoryAdjustments': { es: 'Ajustes de inventario', en: 'Inventory adjustments' },
  'alert.inventoryAdjustmentsDetail': {
    es: '{n} ajuste(s) de inventario impactaron este dia.',
    en: '{n} inventory adjustment(s) impacted this day.',
  },
  'alert.healthyCut': { es: 'Corte sano', en: 'Healthy cut' },
  'alert.healthyCutDetail': {
    es: 'No se detectaron pendientes ni incidencias fuertes para esta fecha.',
    en: 'No pending items or strong incidents detected for this date.',
  },

  // ----- report comparison -----
  'comparison.netSold': { es: 'Vendido neto', en: 'Net sales' },
  'comparison.profit': { es: 'Utilidad', en: 'Profit' },
  'comparison.tickets': { es: 'Tickets cobrados', en: 'Receipts charged' },
  'comparison.refunds': { es: 'Devoluciones', en: 'Refunds' },
  'comparison.noChange': { es: 'Sin cambio contra ayer', en: 'No change vs yesterday' },
  'comparison.moreThan': { es: 'mas que ayer', en: 'more than yesterday' },
  'comparison.lessThan': { es: 'menos que ayer', en: 'less than yesterday' },
  'comparison.noChangeIn': { es: 'Sin cambio en {label}s', en: 'No change in {label}s' },

  // ----- stats / tasks -----
  'stats.currentSale': { es: 'Venta actual', en: 'Current sale' },
  'stats.lines': { es: '{n} partidas', en: '{n} lines' },
  'stats.activeProducts': { es: 'Productos activos', en: 'Active products' },
  'stats.catalog': { es: 'Catalogo', en: 'Catalog' },
  'stats.pending': { es: 'Pendientes', en: 'Pending' },
  'stats.pendingConfirm': { es: 'Por confirmar', en: 'To confirm' },
  'stats.noPending': { es: 'Sin pendientes', en: 'No pending' },
  'stats.paymentMethod': { es: 'Metodo pago', en: 'Payment method' },
  'stats.promo': { es: 'Promo: {code}', en: 'Promo: {code}' },
  'stats.selected': { es: 'Seleccionado', en: 'Selected' },
  'tasks.pendingPayments': {
    es: '{n} pago(s) pendiente(s) por confirmar',
    en: '{n} pending payment(s) to confirm',
  },
  'tasks.restock': { es: 'Reponer {name} ({stock} uds)', en: 'Restock {name} ({stock} pcs)' },
  'tasks.noNews': { es: 'Sin novedades', en: 'Nothing new' },

  // ----- status messages (ok) -----
  'ok.ready': { es: 'Listo para vender', en: 'Ready to sell' },
  'ok.addedToCart': { es: '{name} agregado al carrito', en: '{name} added to cart' },
  'ok.cartUpdated': { es: 'Carrito actualizado', en: 'Cart updated' },
  'ok.cartCleared': { es: 'Venta limpiada', en: 'Sale cleared' },
  'ok.methodSelected': { es: 'Metodo seleccionado: {method}', en: 'Method selected: {method}' },
  'ok.customerSelected': { es: 'Cliente: {name}', en: 'Customer: {name}' },
  'ok.promoRemoved': { es: 'Promo removida', en: 'Promo removed' },
  'ok.promoApplied': { es: 'Promo aplicada: {name}', en: 'Promo applied: {name}' },
  'ok.saleCharged': { es: 'Venta cobrada. Ticket listo.', en: 'Sale charged. Receipt ready.' },
  'ok.saleChargedOpen': {
    es: 'Venta cobrada. Ticket abierto para imprimir.',
    en: 'Sale charged. Receipt open for printing.',
  },
  'ok.paymentConfirmed': { es: 'Pago confirmado', en: 'Payment confirmed' },
  'ok.saleCancelled': {
    es: 'Venta pendiente cancelada y stock repuesto',
    en: 'Pending sale cancelled and stock restored',
  },
  'ok.saleRefunded': {
    es: 'Venta #{id} devuelta, stock repuesto y corte ajustado',
    en: 'Sale #{id} refunded, stock restored and cut adjusted',
  },
  'ok.salePartialRefunded': {
    es: 'Venta #{id} actualizada con devolucion parcial y corte ajustado',
    en: 'Sale #{id} updated with partial refund and cut adjusted',
  },
  'ok.productUpdated': { es: 'Producto actualizado', en: 'Product updated' },
  'ok.productCreated': { es: 'Producto creado', en: 'Product created' },
  'ok.categoryUpdated': { es: 'Categoria actualizada', en: 'Category updated' },
  'ok.categoryCreated': { es: 'Categoria creada', en: 'Category created' },
  'ok.categoryDeleted': { es: 'Categoria eliminada', en: 'Category deleted' },
  'ok.categorySelected': { es: 'Categoria seleccionada: {name}', en: 'Category selected: {name}' },
  'ok.presetLoaded': {
    es: 'Preset cargado para {name}. Guardalo en Categorias para dejarlo fijo.',
    en: 'Preset loaded for {name}. Save it in Categories to keep it fixed.',
  },
  'ok.imageReady': { es: 'Imagen lista: {name}', en: 'Image ready: {name}' },
  'ok.imageCleared': { es: 'Imagen eliminada del formulario', en: 'Image removed from the form' },
  'ok.productDeleted': { es: '{name} eliminado', en: '{name} deleted' },
  'ok.stockUpdated': { es: 'Stock actualizado', en: 'Stock updated' },
  'ok.inventoryAdjustSaved': {
    es: 'Ajuste de inventario guardado',
    en: 'Inventory adjustment saved',
  },
  'ok.purchaseRegistered': {
    es: 'Compra registrada y stock actualizado',
    en: 'Purchase registered and stock updated',
  },
  'ok.purchaseUpdated': {
    es: 'Compra actualizada y stock ajustado',
    en: 'Purchase updated and stock adjusted',
  },
  'ok.purchaseDeleted': {
    es: 'Compra eliminada y stock revertido',
    en: 'Purchase deleted and stock reversed',
  },
  'ok.movementDeleted': {
    es: 'Movimiento eliminado y stock revertido',
    en: 'Movement deleted and stock reversed',
  },
  'ok.customerUpdated': { es: 'Cliente actualizado', en: 'Customer updated' },
  'ok.customerAdded': { es: 'Cliente agregado', en: 'Customer added' },
  'ok.promoUpdated': { es: 'Promo actualizada', en: 'Promo updated' },
  'ok.promoSaved': { es: 'Promo guardada', en: 'Promo saved' },
  'ok.promoDeleted': { es: 'Promo eliminada', en: 'Promo deleted' },
  'ok.customerDeleted': { es: '{name} eliminado', en: '{name} deleted' },
  'ok.ticketSettingsSaved': { es: 'Datos del ticket guardados', en: 'Receipt data saved' },
  'ok.credentialsSaved': {
    es: 'Usuario y contrasena guardados',
    en: 'Username and password saved',
  },
  'ok.accessUpdated': { es: 'Acceso actualizado', en: 'Access updated' },
  'ok.logoLoaded': {
    es: 'Logo cargado, guarda la configuracion',
    en: 'Logo loaded, save the configuration',
  },
  'ok.backupExcel': {
    es: 'Backup formal descargado en Excel',
    en: 'Formal backup downloaded in Excel',
  },
  'ok.backupCsv': { es: 'Backup formal descargado en CSV', en: 'Formal backup downloaded in CSV' },
  'ok.backupPdf': { es: 'Backup formal descargado en PDF', en: 'Formal backup downloaded in PDF' },
  'ok.cashCountSaved': { es: 'Corte de efectivo guardado', en: 'Cash count saved' },
  'ok.dayClosedSuccess': { es: 'Dia cerrado correctamente', en: 'Day closed successfully' },
  'ok.dayReopened': { es: 'Dia reabierto', en: 'Day reopened' },
  'ok.ticketPdfOpened': { es: 'PDF del ticket abierto', en: 'Receipt PDF opened' },
  'ok.ticketPdfOpenedId': { es: 'PDF del ticket #{id} abierto', en: 'Receipt PDF #{id} opened' },
  'ok.reportPdfDownloaded': {
    es: 'PDF del corte diario descargado',
    en: 'Daily report PDF downloaded',
  },
  'ok.accountCreated': {
    es: 'Cuenta creada. Ya puedes iniciar sesion.',
    en: 'Account created. You can sign in now.',
  },
  'ok.passwordUpdated': {
    es: 'Contraseña actualizada. Ya puedes iniciar sesión.',
    en: 'Password updated. You can sign in now.',
  },
  'ok.createNewPassword': {
    es: 'Crea una nueva contraseña para continuar.',
    en: 'Create a new password to continue.',
  },
  'ok.recoveryEmailSent': {
    es: 'Si el correo existe, te enviamos un enlace para restablecer la contraseña.',
    en: "If the email exists, we've sent you a link to reset your password.",
  },
  'ok.onboardingReady': {
    es: 'Pago confirmado. Completa los datos de tu empresa para activar el acceso.',
    en: 'Payment confirmed. Complete your business data to activate access.',
  },

  // ----- status messages (warn) -----
  'warn.promoNotApplicable': {
    es: 'Esa promo no aplica a la venta actual',
    en: "That promo doesn't apply to the current sale",
  },
  'warn.addProductsFirst': {
    es: 'Agrega productos antes de cobrar',
    en: 'Add products before charging',
  },
  'warn.paymentPending': {
    es: 'Pago con {method} pendiente de confirmar.{suffix}',
    en: 'Payment with {method} pending confirmation.{suffix}',
  },
  'warn.ticketOpenSuffix': { es: ' Ticket abierto.', en: ' Receipt open.' },
  'warn.offlineQueued': {
    es: 'Sin conexion. La venta quedo guardada y se sincronizara sola.',
    en: 'No connection. The sale was saved and will sync on its own.',
  },
  'warn.selectRefundUnits': {
    es: 'Selecciona al menos una pieza para devolver o usa "Todo"',
    en: 'Select at least one piece to refund or use "All"',
  },
  'warn.selectProduct': {
    es: 'Selecciona un producto para registrar la compra',
    en: 'Select a product to register the purchase',
  },
  'warn.dayClosed': {
    es: 'El dia esta cerrado. Reabrelo para editar el corte.',
    en: 'The day is closed. Reopen it to edit the cut.',
  },
  'warn.alreadyClosed': { es: 'Este dia ya estaba cerrado', en: 'This day was already closed' },
  'warn.alreadyOpen': { es: 'Ese dia ya esta abierto', en: 'That day is already open' },
  'warn.noRecentTicket': {
    es: 'No hay ticket reciente para imprimir',
    en: 'No recent receipt to print',
  },
  'warn.serverWaking': {
    es: 'El servidor esta despertando, reintentando...',
    en: 'The server is waking up, retrying...',
  },
  'warn.couldNotOpenPdfWindow': {
    es: 'No pude abrir la ventana. Descargue el PDF del ticket.',
    en: 'Could not open the window. The receipt PDF was downloaded.',
  },
  'ok.offlineSyncedOne': {
    es: 'Venta pendiente sincronizada.',
    en: 'Pending sale synced.',
  },
  'ok.offlineSyncedMany': {
    es: '{n} ventas pendientes sincronizadas.',
    en: '{n} pending sales synced.',
  },

  // ----- status messages (err) -----
  'err.productArchived': { es: '{name} esta archivado', en: '{name} is archived' },
  'err.outOfStock': { es: 'Sin stock disponible para {name}', en: 'No stock available for {name}' },
  'err.promoMissing': { es: 'La promo ya no existe', en: 'The promo no longer exists' },
  'err.checkoutFailed': {
    es: 'No se pudo cobrar. Revisa backend o stock.',
    en: 'Could not charge. Check backend or stock.',
  },
  'err.paymentConfirmFailed': {
    es: 'No se pudo confirmar el pago',
    en: 'Could not confirm the payment',
  },
  'err.saleCancelFailed': { es: 'No se pudo cancelar la venta', en: 'Could not cancel the sale' },
  'err.refundFailed': {
    es: 'No se pudo procesar la devolucion',
    en: 'Could not process the refund',
  },
  'err.productNameRequired': { es: 'El producto necesita nombre', en: 'The product needs a name' },
  'err.productUpdateFailed': {
    es: 'No se pudo actualizar el producto',
    en: 'Could not update the product',
  },
  'err.productCreateFailed': {
    es: 'No se pudo crear el producto',
    en: 'Could not create the product',
  },
  'err.categoryNameRequired': {
    es: 'La categoria necesita nombre',
    en: 'The category needs a name',
  },
  'err.categoryUpdateFailed': {
    es: 'No se pudo actualizar la categoria',
    en: 'Could not update the category',
  },
  'err.categoryCreateFailed': {
    es: 'No se pudo crear la categoria',
    en: 'Could not create the category',
  },
  'err.categoryInUse': {
    es: 'No se puede eliminar la categoria porque tiene productos ligados',
    en: 'The category cannot be deleted because it has linked products',
  },
  'err.categoryMissing': {
    es: 'La categoria ya no existe o no pertenece a esta cuenta',
    en: 'The category no longer exists or does not belong to this account',
  },
  'err.categoryDeleteFailed': {
    es: 'No se pudo eliminar la categoria',
    en: 'Could not delete the category',
  },
  'err.imageReadFailed': { es: 'No se pudo leer la imagen', en: 'Could not read the image' },
  'err.productMissing': {
    es: 'El producto ya no existe o no pertenece a esta cuenta',
    en: 'The product no longer exists or does not belong to this account',
  },
  'err.productDeleteFailed': {
    es: 'No se pudo eliminar el producto',
    en: 'Could not delete the product',
  },
  'err.purchaseMinUnits': {
    es: 'La compra necesita al menos 1 unidad',
    en: 'The purchase needs at least 1 unit',
  },
  'err.purchaseFailed': {
    es: 'No se pudo registrar la compra',
    en: 'Could not register the purchase',
  },
  'err.purchaseUpdateFailed': {
    es: 'No se pudo actualizar la compra',
    en: 'Could not update the purchase',
  },
  'err.purchaseDeleteFailed': {
    es: 'No se pudo eliminar la compra',
    en: 'Could not delete the purchase',
  },
  'err.movementDeleteFailed': {
    es: 'No se pudo eliminar el movimiento',
    en: 'Could not delete the movement',
  },
  'err.customerNameRequired': { es: 'El cliente necesita nombre', en: 'The customer needs a name' },
  'err.customerUpdateFailed': {
    es: 'No se pudo actualizar el cliente',
    en: 'Could not update the customer',
  },
  'err.customerAddFailed': {
    es: 'No se pudo agregar el cliente',
    en: 'Could not add the customer',
  },
  'err.promoNameRequired': { es: 'La promo necesita nombre', en: 'The promo needs a name' },
  'err.promoCodeRequired': { es: 'La promo necesita codigo', en: 'The promo needs a code' },
  'err.promoValueRequired': {
    es: 'La promo necesita un valor mayor a 0',
    en: 'The promo needs a value greater than 0',
  },
  'err.promoPercentMax': { es: 'El porcentaje maximo es 100', en: 'The maximum percentage is 100' },
  'err.promoDateRange': {
    es: 'La fecha final no puede ser menor a la inicial',
    en: 'The end date cannot be earlier than the start date',
  },
  'err.promoCodeExists': { es: 'Ese codigo ya existe', en: 'That code already exists' },
  'err.customerMissing': {
    es: 'El cliente ya no existe o no pertenece a esta cuenta',
    en: 'The customer no longer exists or does not belong to this account',
  },
  'err.customerDeleteFailed': {
    es: 'No se pudo eliminar el cliente',
    en: 'Could not delete the customer',
  },
  'err.storeNameRequired': {
    es: 'El nombre de la tienda es obligatorio',
    en: 'The store name is required',
  },
  'err.ticketSettingsFailed': {
    es: 'No se pudieron guardar los datos del ticket',
    en: 'Could not save the receipt data',
  },
  'err.usernameRequired': { es: 'El usuario es obligatorio', en: 'The username is required' },
  'err.currentPasswordRequired': {
    es: 'Escribe la contrasena actual para confirmar el cambio',
    en: 'Enter the current password to confirm the change',
  },
  'err.credentialsFailed': {
    es: 'No se guardo: revisa la contrasena actual',
    en: 'Not saved: check the current password',
  },
  'err.cashCountFailed': {
    es: 'No se pudo guardar el efectivo real',
    en: 'Could not save the actual cash',
  },
  'err.couldNotLoadProducts': {
    es: 'No pude cargar productos del backend',
    en: 'Could not load products from the backend',
  },
  'err.couldNotLoadCategories': {
    es: 'No se pudieron cargar las categorias',
    en: 'Could not load the categories',
  },
  'err.couldNotLoadReport': {
    es: 'No pude cargar el corte del dia',
    en: 'Could not load the daily report',
  },
  'err.couldNotLoadCustomers': {
    es: 'No pude cargar clientes del backend',
    en: 'Could not load customers from the backend',
  },
  'err.couldNotUpdateInventory': {
    es: 'No se pudo actualizar inventario',
    en: 'Could not update inventory',
  },
  'err.couldNotLoadSettings': {
    es: 'No se pudo cargar la configuracion',
    en: 'Could not load the settings',
  },
  'err.backupFailed': { es: 'No se pudo generar el backup', en: 'Could not generate the backup' },
  'err.closeDayFailed': { es: 'No se pudo cerrar el dia', en: 'Could not close the day' },
  'err.reopenDayFailed': { es: 'No se pudo reabrir el dia', en: 'Could not reopen the day' },
  'err.loginFieldsRequired': {
    es: 'Escribe usuario y contraseña',
    en: 'Enter username and password',
  },
  'err.invalidCredentials': {
    es: 'Usuario o contraseña incorrectos',
    en: 'Incorrect username or password',
  },
  'err.tooManyAttempts': {
    es: 'Demasiados intentos. Espera unos minutos e intenta de nuevo.',
    en: 'Too many attempts. Wait a few minutes and try again.',
  },
  'err.backendTimeout': {
    es: 'El backend no respondio a tiempo. Si el servidor estaba dormido, espera y vuelve a intentar en {url}',
    en: 'The backend did not respond in time. If the server was asleep, wait and try again at {url}',
  },
  'err.backendUnreachable': {
    es: 'No pude conectar con el backend. Revisa {url}',
    en: 'Could not connect to the backend. Check {url}',
  },
  'err.recoveryUserRequired': {
    es: 'Escribe tu correo o usuario',
    en: 'Enter your email or username',
  },
  'err.recoveryRoute': {
    es: 'El backend rechazo la solicitud de recuperacion. Revisa que esa ruta este publica y reinicia el backend.',
    en: 'The backend rejected the recovery request. Check that this route is public and restart the backend.',
  },
  'err.recoveryStartFailed': {
    es: 'No pude iniciar la recuperación en este momento.',
    en: 'Could not start the recovery right now.',
  },
  'err.resetLinkInvalid': {
    es: 'El enlace de recuperación ya no es válido.',
    en: 'The recovery link is no longer valid.',
  },
  'err.resetPassRequired': {
    es: 'Escribe y confirma la nueva contraseña',
    en: 'Enter and confirm the new password',
  },
  'err.passwordsMismatch': { es: 'Las contraseñas no coinciden', en: 'Passwords do not match' },
  'err.resetPassFormat': {
    es: 'La contraseña debe tener mayúscula, minúscula y al menos un número.',
    en: 'The password must have an uppercase, a lowercase and at least one number.',
  },
  'err.resetLinkExpired': {
    es: 'El enlace de recuperación expiró. Solicita uno nuevo.',
    en: 'The recovery link expired. Request a new one.',
  },
  'err.operationTimeout': {
    es: 'La operación tardó demasiado. Intenta otra vez.',
    en: 'The operation took too long. Try again.',
  },
  'err.resetGeneral': {
    es: 'No pude completar la recuperación en este momento.',
    en: 'Could not complete the recovery right now.',
  },
  'err.onboardingPrepareFailed': {
    es: 'No pude preparar la activacion.',
    en: 'Could not prepare the activation.',
  },
  'err.onboardingStripeFailed': {
    es: 'No pude validar el pago con Stripe.',
    en: 'Could not validate the payment with Stripe.',
  },
  'err.onboardingTimeout': {
    es: 'La validacion con Stripe tardo demasiado. Reintenta en unos segundos.',
    en: 'The Stripe validation took too long. Retry in a few seconds.',
  },
  'err.onboardingPaymentInvalid': {
    es: 'Stripe no confirmo un pago valido para esta activacion.',
    en: 'Stripe did not confirm a valid payment for this activation.',
  },
  'err.onboardingLinkInvalid': {
    es: 'El enlace de activacion ya no es valido.',
    en: 'The activation link is no longer valid.',
  },
  'err.onboardingAlreadyUsed': {
    es: 'Esta cuenta ya fue activada o este pago ya se uso.',
    en: 'This account was already activated or this payment was already used.',
  },
  'err.onboardingLinkExpired': {
    es: 'El enlace de activacion expiro. Vuelve desde Stripe.',
    en: 'The activation link expired. Come back from Stripe.',
  },
  'err.onboardingStripeNotConfigured': {
    es: 'Stripe no esta configurado todavia en el backend.',
    en: 'Stripe is not configured yet in the backend.',
  },
  'err.onboardingSessionInvalid': {
    es: 'La sesion de activacion no es valida.',
    en: 'The activation session is not valid.',
  },
  'err.onboardingIncomplete': {
    es: 'Completa nombre del negocio, correo y contraseña.',
    en: 'Complete business name, email and password.',
  },
  'err.passwordTooShort': {
    es: 'La contraseña debe tener al menos 8 caracteres.',
    en: 'The password must have at least 8 characters.',
  },
  'err.onboardingCompleteFailed': {
    es: 'No pude completar la activacion.',
    en: 'Could not complete the activation.',
  },
  'err.sessionExpired': {
    es: 'Tu sesion expiro. Vuelve a entrar.',
    en: 'Your session expired. Sign in again.',
  },
  'err.sessionExpiredAlert': {
    es: 'La sesion expiro. Vuelve a iniciar sesion.',
    en: 'The session expired. Sign in again.',
  },
};

@Injectable({ providedIn: 'root' })
export class LanguageService {
  readonly lang = signal<AppLang>(this.load());

  readonly t = (key: string, params?: TranslateParams): string => {
    const entry = TRANSLATIONS[key];
    let text = entry ? entry[this.lang()] : key;
    if (params) {
      for (const [name, value] of Object.entries(params)) {
        text = text.split(`{${name}}`).join(String(value));
      }
    }
    return text;
  };

  readonly toggleLang = (): void => {
    this.lang.update((current) => (current === 'es' ? 'en' : 'es'));
    this.persist(this.lang());
  };

  setLang(lang: AppLang): void {
    this.lang.set(lang);
    this.persist(lang);
  }

  private load(): AppLang {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      return stored === 'en' ? 'en' : 'es';
    } catch {
      return 'es';
    }
  }

  private persist(lang: AppLang): void {
    try {
      localStorage.setItem(STORAGE_KEY, lang);
    } catch {
      // storage unavailable (SSR, private mode)
    }
  }
}
