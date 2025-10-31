const mysql = require("mysql2/promise");
require("dotenv").config();

const pool = mysql.createPool({
  host: process.env.DB_HOST || "localhost",
  user: process.env.DB_USER || "root",
  password: process.env.DB_PASSWORD || "",   // empty cuz we didn't set one 
  database: process.env.DB_DATABASE || "ecoWattch_db",
  port: process.env.DB_PORT || 3001,        
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0
});

module.exports = pool;