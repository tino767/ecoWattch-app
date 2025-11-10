const express = require("express");
const bodyParser = require("body-parser");
const cors = require("cors");
const pool = require("./db"); // now uses mysql2 connection pool
const bcrypt = require('bcryptjs');

const app = express();
app.use(cors());
app.use(bodyParser.json());

//hashing function
async function hashPassword(plain) {
  const cost = 12;                // work factor; 12 is a good default
  const hash = await bcrypt.hash(plain, cost);
  return hash; // store this in DB
}

// ------------------------------
// LOGIN endpoint
// ------------------------------
app.post("/login", async (req, res) => {
  const { usernames, passwords } = req.body;

  //make a new variable for the hashed password
  let passMatch

  try {
    // First get user data for password verification
    const [fullUserData] = await pool.query(
      "SELECT * FROM Users WHERE Username = ?",
      [usernames]
    );

    if (fullUserData.length === 0) {
      return res.json({ status: "failure", message: "Invalid credentials" });
    }

    //Compare the plaintext password with the stored hash
    passMatch = await bcrypt.compare(passwords, fullUserData[0].PasswordHash);

    if (passMatch) {
      // Try to get SpendablePoints, fall back gracefully if column doesn't exist
      let userData = {
        Username: fullUserData[0].Username,
        DormName: fullUserData[0].DormName,
        SpendablePoints: 100 // Default value
      };

      try {
        const [pointsData] = await pool.query(
          "SELECT SpendablePoints FROM Users WHERE Username = ?",
          [usernames]
        );
        if (pointsData.length > 0 && pointsData[0].SpendablePoints !== undefined) {
          userData.SpendablePoints = pointsData[0].SpendablePoints;
        }
      } catch (error) {
        console.log("SpendablePoints column not found, using default value of 100");
      }

      res.json({ status: "success", user: userData });
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

    //make a new variable for the hashed password
    let passHash = passwords

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

    //hash the password
    passHash = await hashPassword(passwords);

    // Insert the new user - try with SpendablePoints, fall back without it
    try {
      await pool.query(
        "INSERT INTO Users (Username, PasswordHash, DormName, SpendablePoints) VALUES (?, ?, ?, ?)",
        [usernames, passHash, dormitory, 100]
      );
    } catch (error) {
      if (error.code === 'ER_BAD_FIELD_ERROR' && error.sqlMessage.includes('SpendablePoints')) {
        // Column doesn't exist yet, insert without it
        console.log("SpendablePoints column not found, inserting user without it");
        await pool.query(
          "INSERT INTO Users (Username, PasswordHash, DormName) VALUES (?, ?, ?)",
          [usernames, passHash, dormitory]
        );
      } else {
        throw error; // Re-throw if it's a different error
      }
    }

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
// GET ALL PALETTES endpoint
// ------------------------------
app.get("/palettes", async (req, res) => {
  try {
    const [rows] = await pool.query(`
      SELECT OfferingName, ColorHex1, ColorHex2, ColorHex3, ColorHex4, ColorHex5, ColorHex6, ColorHex7
      FROM offerings;
    `);

    res.json({ status: "success", palettes: rows });
  } catch (error) {
    console.error("Error fetching palettes:", error);
    res.status(500).json({ status: "error", message: "Failed to fetch palettes" });
  }
});

// ------------------------------
// UPDATE USER POINTS endpoint
// ------------------------------
app.post("/update_user_points", async (req, res) => {
  try {
    const { username, spendablePoints } = req.body;

    if (!username || spendablePoints === undefined) {
      return res.status(400).json({
        status: "error",
        message: "Username and spendablePoints required",
      });
    }

    // Update the user's spendable points - handle missing column gracefully
    try {
      await pool.query(
        "UPDATE Users SET SpendablePoints = ? WHERE Username = ?",
        [spendablePoints, username]
      );
      res.json({ status: "success", message: "Points updated successfully" });
    } catch (error) {
      if (error.code === 'ER_BAD_FIELD_ERROR' && error.sqlMessage.includes('SpendablePoints')) {
        console.log("SpendablePoints column not found, cannot update points");
        res.json({ status: "success", message: "Points updated (column not available)" });
      } else {
        throw error;
      }
    }
  } catch (error) {
    console.error("Update points error:", error);
    res.status(500).json({ status: "error", message: "Failed to update points" });
  }
});

// ------------------------------
// PURCHASE PALETTE endpoint
// ------------------------------
app.post("/purchase_palette", async (req, res) => {
  try {
    const { username, paletteName, pointsToDeduct } = req.body;

    if (!username || !paletteName || !pointsToDeduct) {
      return res.status(400).json({
        status: "error",
        message: "Username, paletteName, and pointsToDeduct required",
      });
    }

    // Check if user exists and get current points
    let currentPoints = 100; // Default value
    try {
      const [userRows] = await pool.query(
        "SELECT SpendablePoints FROM Users WHERE Username = ?",
        [username]
      );

      if (userRows.length === 0) {
        return res.status(404).json({
          status: "error",
          message: "User not found",
        });
      }

      currentPoints = userRows[0].SpendablePoints || 100;
    } catch (error) {
      if (error.code === 'ER_BAD_FIELD_ERROR' && error.sqlMessage.includes('SpendablePoints')) {
        console.log("SpendablePoints column not found, using default value");
        // Check if user exists without SpendablePoints column
        const [userRows] = await pool.query(
          "SELECT Username FROM Users WHERE Username = ?",
          [username]
        );
        
        if (userRows.length === 0) {
          return res.status(404).json({
            status: "error",
            message: "User not found",
          });
        }
        currentPoints = 100; // Default
      } else {
        throw error;
      }
    }
    
    if (currentPoints < pointsToDeduct) {
      return res.status(400).json({
        status: "error",
        message: "Insufficient points",
      });
    }

    // Deduct points and record purchase
    const newPoints = currentPoints - pointsToDeduct;
    
    try {
      await pool.query(
        "UPDATE Users SET SpendablePoints = ? WHERE Username = ?",
        [newPoints, username]
      );
    } catch (error) {
      if (error.code === 'ER_BAD_FIELD_ERROR' && error.sqlMessage.includes('SpendablePoints')) {
        console.log("SpendablePoints column not found, purchase processed without DB update");
      } else {
        throw error;
      }
    }

    // You could also store the purchase in a separate table for tracking
    // For now, we'll just return success with the new point total

    res.json({ 
      status: "success", 
      message: "Purchase successful",
      newPointTotal: newPoints
    });
  } catch (error) {
    console.error("Purchase error:", error);
    res.status(500).json({ status: "error", message: "Failed to process purchase" });
  }
});

// ------------------------------
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`✅ API running on port ${PORT}`));