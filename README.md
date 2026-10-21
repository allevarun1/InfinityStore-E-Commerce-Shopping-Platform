# 🛒 Rekha Shopping Mall - E-Commerce Shopping Platform

A full-stack E-Commerce Shopping Platform built using **Spring Boot**, **React.js**, and **MySQL**. The application provides secure user authentication, product browsing, shopping cart management, wishlist functionality, order processing, and an admin dashboard for managing products and categories.

---

## 🚀 Features

### 👤 User Features

- User Registration & Login
- JWT Authentication
- Forgot Password & Reset Password
- Browse Products
- Search & Filter Products
- Product Details
- Shopping Cart
- Wishlist
- Checkout
- Order History
- Profile Management

### 🛠️ Admin Features

- Secure Admin Login
- Manage Products (CRUD)
- Manage Categories
- Manage Orders
- Manage Users
- Dashboard Overview

---

# 🏗️ Tech Stack

## Frontend

- React.js
- Vite
- React Router DOM
- Axios
- CSS3
- HTML5
- JavaScript (ES6)

## Backend

- Java 17
- Spring Boot
- Spring Security
- JWT Authentication
- Spring Data JPA
- Hibernate
- REST APIs
- Maven

## Database

- MySQL

---

# 📂 Project Structure

```
Rekha Shopping Mall
│
├── ecommerce-backend
│   ├── src
│   ├── pom.xml
│   └── application.properties
│
├── ecommerce-frontend
│   ├── src
│   ├── public
│   ├── package.json
│   └── vite.config.js
│
└── README.md
```

---

# 🔐 Authentication

- JWT Token Authentication
- Role-Based Authorization
- Secure Password Encryption
- Protected REST APIs

---

# 📦 Backend Modules

- Authentication
- User Management
- Product Management
- Category Management
- Shopping Cart
- Wishlist
- Order Management
- Email Service
- Exception Handling
- Security Configuration

---

# 🎨 Frontend Modules

- Home
- Login
- Register
- Products
- Product Details
- Cart
- Wishlist
- Checkout
- Orders
- Profile
- Admin Dashboard

---

# ⚙️ Installation

## Clone Repository

```bash
git clone https://github.com/madhumadhu999888-sudo/InfinityStore-E-Commerce-Shopping-Platform.git
```

---

## Backend Setup

```bash
cd ecommerce-backend
```

Configure MySQL in

```
application.properties
```

Run

```bash
mvn spring-boot:run
```

Backend runs on

```
http://localhost:8080
```

---

## Frontend Setup

```bash
cd ecommerce-frontend
```

Install dependencies

```bash
npm install
```

Run

```bash
npm run dev
```

Frontend runs on

```
http://localhost:5173
```

---

# 📡 REST API

## Authentication

- POST /api/auth/signup
- POST /api/auth/signin
- POST /api/auth/forgot-password
- POST /api/auth/reset-password

## Products

- GET /api/products
- GET /api/products/{id}
- POST /api/products
- PUT /api/products/{id}
- DELETE /api/products/{id}

## Categories

- GET /api/categories
- POST /api/categories

## Cart

- Add Item
- Update Quantity
- Remove Item

## Wishlist

- Add Product
- Remove Product
- View Wishlist

## Orders

- Place Order
- Order History
- Update Order Status

---

# 🛡️ Security

- Spring Security
- JWT Authentication
- Password Encryption
- Role-Based Access Control

---

# 📸 Screenshots

Add screenshots here.

Example

```
screenshots/
│
├── home.png
├── login.png
├── products.png
├── cart.png
├── admin.png
```

---

# 🌟 Future Enhancements

- Payment Gateway Integration
- Product Reviews
- Product Ratings
- Coupon System
- Order Tracking
- Email Notifications
- Docker Deployment
- Cloud Deployment

---

# 👨‍💻 Author

**Madhusudhan Bhimanathini**

GitHub

https://github.com/madhumadhu999888-sudo

---

# ⭐ Support

If you found this project useful, please consider giving it a ⭐ on GitHub.
