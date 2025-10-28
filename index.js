const express = require("express");
const bodyParser = require("body-parser");
const cors = require("cors");
const pool = require("./db"); // now uses mysql2 connection pool

const app = express();
app.use(cors());
app.use(bodyParser.json());

// ------------------------------
// LOGIN endpoint
// ------------------------------
app.post("/login", async (req, res) => {
  const { usernames, passwords } = req.body;

  try {
    const [rows] = await pool.query(
      "SELECT * FROM Users WHERE Username = ? AND PasswordHash = ?",
      [usernames, passwords]
    );

    if (rows.length > 0) {
      res.json({ status: "success", user: rows[0] });
    } else {
      res.json({ status: "failure", message: "Invalid credentials" });
    }
  } catch (err) {
    console.error("Login error:", err);
    res.status(500).json({ status: "error", message: "Server error" });
  }
});

// ------------------------------
// SIGNUP endpoint
// ------------------------------
app.post("/signup", async (req, res) => {
  try {
    const { usernames, passwords, dormitory } = req.body;

    if (!usernames || !passwords) {
      return res.status(400).json({
        status: "error",
        message: "Username and password required",
      });
    }

    // Check if username already exists
    const [existing] = await pool.query(
      "SELECT * FROM Users WHERE Username = ?",
      [usernames]
    );

    if (existing.length > 0) {
      return res
        .status(409)
        .json({ status: "error", message: "Username already exists" });
    }

    // Insert the new user
    await pool.query(
      "INSERT INTO Users (Username, PasswordHash, DormName) VALUES (?, ?, ?)",
      [usernames, passwords, dormitory]
    );

    res
      .status(201)
      .json({ status: "success", message: "User created successfully" });
  } catch (error) {
    console.error("Signup error:", error);
    res
      .status(500)
      .json({ status: "error", message: "Internal server error" });
  }
});

// ------------------------------
// DORM POINTS endpoint
// ------------------------------
app.post("/dorm_points", async (req, res) => {
  try {
    const { Tinsley_total_points, Sechrist_total_points, Gabaldon_total_points } = req.body;

    //define the names of the dorms 
    const Dorm1_name = 'Tinsley'; // Tinsley
    const Dorm2_name = 'Sechrist'; // Sechrist
    const Dorm3_name = 'Gabaldon'; // Gabaldon

    // add the points to the given dorm 
    await pool.query(`
      UPDATE Dorms SET TotalPoints = TotalPoints +
        CASE DormName
          WHEN ? THEN ?
          WHEN ? THEN ?
          WHEN ? THEN ?
          ELSE 0
        END
      WHERE DormName IN (?, ?, ?);`,
      [Dorm1_name, Tinsley_total_points, Dorm2_name, Sechrist_total_points, Dorm3_name, Gabaldon_total_points, Dorm1_name, Dorm2_name, Dorm3_name]
    );

    res
      .status(201)
      .json({ status: "success", message: "Points added successfully" });
  } catch (error) {
    console.error("Points error:", error);
    res
      .status(500)
      .json({ status: "error", message: "Something went wrong with the points" });
  }

});

// ------------------------------
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`✅ API running on port ${PORT}`));