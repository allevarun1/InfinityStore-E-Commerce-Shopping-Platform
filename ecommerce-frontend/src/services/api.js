import axios from 'axios';

// Set VITE_API_URL in the deployment environment (e.g. Railway) to point at the
// backend, for example https://your-backend.up.railway.app/api
const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080/api',
});

// Interceptor to add JWT token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('infinity_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// An expired or revoked token makes every protected call fail with 401 while the UI still
// looks signed in. Drop the stale session and send the customer back to sign in.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const hadToken = !!localStorage.getItem('infinity_token');
    if (error.response?.status === 401 && hadToken) {
      localStorage.removeItem('infinity_token');
      localStorage.removeItem('infinity_user');
      if (window.location.pathname !== '/login') {
        window.location.assign('/login?expired=1');
      }
    }
    return Promise.reject(error);
  }
);

export const ApiService = {
  storeInfo: () => api.get('/store/info').then(res => res.data),
  products: () => api.get('/products').then(res => res.data),
  product: (id) => api.get(`/products/${id}`).then(res => res.data),
  deals: () => api.get('/products/deals').then(res => res.data),
  searchProducts: (query) => api.get(`/products/search?q=${encodeURIComponent(query)}`).then(res => res.data),
  categories: () => api.get('/categories').then(res => res.data),
  
  cart: () => api.get('/cart').then(res => res.data),
  addToCart: (productId, quantity = 1) => api.post('/cart', { productId, quantity }).then(res => res.data),
  updateCart: (id, quantity) => api.put(`/cart/${id}?quantity=${quantity}`).then(res => res.data),
  removeCart: (id) => api.delete(`/cart/${id}`).then(res => res.data),
  
  referral: () => api.get('/referral').then(res => res.data),
  wishlist: () => api.get('/wishlist').then(res => res.data),
  addWishlist: (productId) => api.post(`/wishlist/${productId}`).then(res => res.data),
  removeWishlist: (productId) => api.delete(`/wishlist/${productId}`).then(res => res.data),
  
  reviews: (productId) => api.get(`/products/${productId}/reviews`).then(res => res.data),
  reviewEligibility: (productId) => api.get(`/reviews/eligibility/${productId}`).then(res => res.data),
  submitReview: (productId, payload) => api.post(`/reviews/product/${productId}`, payload).then(res => res.data),
  hideReview: (id, hidden) => api.put(`/reviews/${id}/hidden?hidden=${hidden}`).then(res => res.data),

  cartQuote: (coupon) => api.get('/cart/quote', { params: coupon ? { coupon } : {} }).then(res => res.data),
  subscribe: (email) => api.post('/store/subscribe', { email, source: 'footer' }).then(res => res.data),
  colours: (id) => api.get(`/products/${id}/colours`).then(res => res.data),
  addColour: (id, colour) => api.post(`/admin/products/${id}/colour`, null, { params: { colour } }).then(res => res.data),
  catalogueTemplate: () => api.get('/admin/catalog/template', { responseType: 'blob' }).then(res => res.data),
  catalogueExport: () => api.get('/admin/catalog/export', { responseType: 'blob' }).then(res => res.data),
  importCatalogue: (csv, dryRun) => api.post('/admin/catalog/import', csv, { params: { dryRun }, headers: { 'Content-Type': 'text/csv' } }).then(res => res.data),
  delivery: (pincode) => api.get('/store/delivery', { params: { pincode } }).then(res => res.data),
  trackOrder: (orderNumber, phone) => api.post('/store/track', { orderNumber, phone }).then(res => res.data),
  offers: () => api.get('/store/offers').then(res => res.data),
  testimonials: () => api.get('/store/testimonials').then(res => res.data),
  adminTestimonials: () => api.get('/admin/testimonials').then(res => res.data),
  createTestimonial: (payload) => api.post('/admin/testimonials', payload).then(res => res.data),
  setTestimonialActive: (id, value) => api.put(`/admin/testimonials/${id}/active`, null, { params: { value } }).then(res => res.data),
  deleteTestimonial: (id) => api.delete(`/admin/testimonials/${id}`).then(res => res.data),
  storeVideos: () => api.get('/store/videos').then(res => res.data),
  adminVideos: () => api.get('/admin/videos').then(res => res.data),
  createVideo: (payload) => api.post('/admin/videos', payload).then(res => res.data),
  setVideoActive: (id, value) => api.put(`/admin/videos/${id}/active`, null, { params: { value } }).then(res => res.data),
  deleteVideo: (id) => api.delete(`/admin/videos/${id}`).then(res => res.data),
  adminOffers: () => api.get('/admin/offers').then(res => res.data),
  createOffer: (payload) => api.post('/admin/offers', payload).then(res => res.data),
  setOfferActive: (id, value) => api.put(`/admin/offers/${id}/active`, null, { params: { value } }).then(res => res.data),
  deleteOffer: (id) => api.delete(`/admin/offers/${id}`).then(res => res.data),
  captureLead: (payload) => api.post('/store/leads', payload).then(res => res.data),
  assistantChat: (message, filters) => api.post('/assistant/chat', { message, filters }).then(res => res.data),
  adminLeads: () => api.get('/admin/leads').then(res => res.data),
  updateLead: (id, patch) => api.patch(`/admin/leads/${id}`, patch).then(res => res.data),
  exportLeads: () => api.get('/admin/leads/export', { responseType: 'blob' }).then(res => res.data),
  autopilot: () => api.get('/admin/autopilot').then(res => res.data),
  setAutopilotMode: (key, mode) => api.put(`/admin/autopilot/rules/${key}`, null, { params: { mode } }).then(res => res.data),
  runAutopilot: () => api.post('/admin/autopilot/run').then(res => res.data),
  sendAutopilotMessage: (id) => api.post(`/admin/autopilot/events/${id}/send`).then(res => res.data),
  closeAutopilotMessage: (id, sent) => api.post(`/admin/autopilot/events/${id}/close`, null, { params: { sent } }).then(res => res.data),
  adminCoupons: () => api.get('/admin/coupons').then(res => res.data),
  createCoupon: (payload) => api.post('/admin/coupons', payload).then(res => res.data),
  setCouponActive: (id, value) => api.put(`/admin/coupons/${id}/active`, null, { params: { value } }).then(res => res.data),
  paymentOptions: () => api.get('/orders/payment-options').then(res => res.data),
  checkout: (payload, idempotencyKey) => api.post('/orders/checkout', payload, {
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {},
  }).then(res => res.data),
  cancelOrder: (id) => api.post(`/orders/${id}/cancel`).then(res => res.data),
  orders: () => api.get('/orders/history').then(res => res.data),
  
  profile: () => api.get('/user/profile').then(res => res.data),
  updateProfile: (payload) => api.put('/user/profile', payload).then(res => res.data),
  deleteAccount: () => api.delete('/user/profile').then(res => res.data),
  
  adminUsers: () => api.get('/admin/users').then(res => res.data),
  adminProducts: () => api.get('/admin/products').then(res => res.data),
  setProductStatus: (id, to) => api.put(`/products/${id}/status?to=${to}`).then(res => res.data),
  insightsDashboard: (days) => api.get(`/admin/insights/dashboard?days=${days}`).then(res => res.data),
  insightsOpportunities: () => api.get('/admin/insights/opportunities').then(res => res.data),
  studioStatus: () => api.get('/admin/studio/status').then(res => res.data),
  studioDraft: (payload) => api.post('/admin/studio/draft', payload).then(res => res.data),
  adminOrders: () => api.get('/admin/orders').then(res => res.data),
  
  saveProduct: (product) => {
    return product.id
      ? api.put(`/products/${product.id}`, product).then(res => res.data)
      : api.post('/products', product).then(res => res.data);
  },
  deleteProduct: (id) => api.delete(`/products/${id}`).then(res => res.data),
  
  saveCategory: (category) => {
    return category.id
      ? api.put(`/categories/${category.id}`, category).then(res => res.data)
      : api.post('/categories', category).then(res => res.data);
  },
  deleteCategory: (id) => api.delete(`/categories/${id}`).then(res => res.data),
  
  deleteUser: (id) => api.delete(`/admin/users/${id}`).then(res => res.data),
  updateDeliveryStatus: (id, status) => api.put(`/admin/orders/${id}/delivery-status`, { status }).then(res => res.data),
  updatePaymentStatus: (id, status) => api.put(`/admin/orders/${id}/payment-status`, { status }).then(res => res.data),
  markStock: (productId, inStock) => api.put(`/products/${productId}/stock?inStock=${inStock}`).then(res => res.data)
};
