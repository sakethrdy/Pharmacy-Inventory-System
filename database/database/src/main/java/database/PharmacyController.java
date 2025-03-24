package database;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.annotation.PostConstruct;

import java.sql.Date;
import java.sql.ResultSet;
import java.util.List;
import java.util.Random;

@Controller
public class PharmacyController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initDatabase() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS medicines (
                id INT PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                description VARCHAR(255),
                price DECIMAL(10, 2) NOT NULL,
                stock_quantity INT NOT NULL,
                expiry_date DATE
            )
        """;
        jdbcTemplate.update(createTableSQL);

        String countSQL = "SELECT COUNT(*) FROM medicines";
        int count = jdbcTemplate.queryForObject(countSQL, Integer.class);
        if (count == 0) {
            // Prepare to insert 200 unique medicines
            String insertSQL = "INSERT INTO medicines (id, name, description, price, stock_quantity, expiry_date) VALUES (?, ?, ?, ?, ?, ?)";
            Random rand = new Random();

            // Arrays of base medicine names and descriptions
            String[] baseMedicineNames = {
                "Paracetamol", "Ibuprofen", "Aspirin", "Cough Syrup", "Vitamin C", "Amoxicillin", "Cetirizine", "Omeprazole",
                "Metformin", "Losartan", "Atorvastatin", "Salbutamol", "Diazepam", "Loratadine", "Ranitidine", "Ciprofloxacin",
                "Levothyroxine", "Prednisolone", "Tramadol", "Clopidogrel", "Furosemide", "Hydrochlorothiazide", "Azithromycin",
                "Pantoprazole", "Gabapentin", "Doxycycline", "Simvastatin", "Warfarin", "Montelukast", "Fluoxetine"
            };
            String[] descriptions = {
                "Pain Relief", "Anti-inflammatory", "Headache Relief", "Soothes Throat", "Immunity Boost", "Antibiotic",
                "Antihistamine", "Acid Reducer", "Diabetes Control", "Blood Pressure", "Cholesterol Control", "Asthma Relief",
                "Anxiety Relief", "Allergy Relief", "Stomach Ulcer", "Infection Fighter", "Thyroid Support", "Steroid",
                "Pain Management", "Blood Thinner", "Diuretic", "Antiviral", "Seizure Control", "Mood Stabilizer"
            };

            // Generate 200 unique medicines
            for (int i = 1; i <= 200; i++) {
                String name = baseMedicineNames[rand.nextInt(baseMedicineNames.length)] + (i % 10 == 0 ? " Extra" : " " + i);
                String description = descriptions[rand.nextInt(descriptions.length)];
                double price = 10 + rand.nextDouble() * 490; // Price between 10 and 500
                int stock = rand.nextInt(200) + 10; // Stock between 10 and 210
                String expiry = String.format("202%d-%02d-%02d", rand.nextInt(3) + 5, rand.nextInt(12) + 1, rand.nextInt(28) + 1); // 2025-2027

                jdbcTemplate.update(insertSQL, i, name, description, price, stock, Date.valueOf(expiry));
            }
            System.out.println("200 unique medicines loaded into the database.");
        }
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("medicines", getAllMedicines());
        return "home";
    }

    @GetMapping("/add")
    public String addMedicinePage() {
        return "add";
    }

    @PostMapping("/add")
    public String addMedicine(
            @RequestParam("id") int id,
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("price") double price,
            @RequestParam("stockQuantity") int stockQuantity,
            @RequestParam("expiryDate") String expiryDate,
            Model model) {
        String sql = "INSERT INTO medicines (id, name, description, price, stock_quantity, expiry_date) VALUES (?, ?, ?, ?, ?, ?)";
        try {
            jdbcTemplate.update(sql, id, name, description, price, stockQuantity, Date.valueOf(expiryDate));
            model.addAttribute("message", "Medicine added successfully!");
        } catch (Exception e) {
            model.addAttribute("message", "Error: ID might already exist or invalid input. " + e.getMessage());
        }
        return "add";
    }

    @GetMapping("/update")
    public String updateMedicinePage() {
        return "update";
    }

    @PostMapping("/update")
    public String updateMedicine(
            @RequestParam("id") int id,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "price", required = false, defaultValue = "0") double price,
            Model model) {
        String sql = "UPDATE medicines SET name = COALESCE(NULLIF(?, ''), name), price = COALESCE(NULLIF(?, 0), price) WHERE id = ?";
        int rows = jdbcTemplate.update(sql, name, price, id);
        model.addAttribute("message", rows > 0 ? "Medicine updated successfully!" : "ID not found.");
        return "update";
    }

    @GetMapping("/delete")
    public String deleteMedicinePage() {
        return "delete";
    }

    @PostMapping("/delete")
    public String deleteMedicine(@RequestParam("id") int id, Model model) {
        String sql = "DELETE FROM medicines WHERE id = ?";
        int rows = jdbcTemplate.update(sql, id);
        model.addAttribute("message", rows > 0 ? "Medicine deleted successfully!" : "ID not found.");
        return "delete";
    }

    @GetMapping("/search")
    public String searchMedicinePage() {
        return "search";
    }

    @PostMapping("/search")
    public String searchMedicine(@RequestParam("name") String name, Model model) {
        model.addAttribute("medicines", searchMedicinesByName(name));
        return "search";
    }

    @GetMapping("/batch")
    public String batchUpdatePage(Model model) {
        batchUpdateStock();
        model.addAttribute("message", "Batch update completed!");
        model.addAttribute("medicines", getAllMedicines());
        return "batch";
    }

    @GetMapping("/expired")
    public String expiredMedicines(Model model) {
        model.addAttribute("medicines", getExpiredMedicines());
        return "expired";
    }

    @GetMapping("/ai-suggest")
    public String aiSuggestPage() {
        return "ai-suggest";
    }

    @PostMapping("/ai-suggest")
    public String aiSuggest(@RequestParam("problem") String problem, Model model) {
        Medicine suggestion = suggestMedicine(problem);
        if (suggestion != null) {
            model.addAttribute("suggestion", suggestion);
        } else {
            model.addAttribute("message", "No suitable medicine found for your problem. Please try again or consult a doctor.");
        }
        return "ai-suggest";
    }

    private List<Medicine> getAllMedicines() {
        String sql = "SELECT * FROM medicines";
        return jdbcTemplate.query(sql, this::mapRowToMedicine);
    }

    private List<Medicine> searchMedicinesByName(String name) {
        String sql = "SELECT * FROM medicines WHERE name LIKE ?";
        return jdbcTemplate.query(sql, new Object[]{"%" + name + "%"}, this::mapRowToMedicine);
    }

    private List<Medicine> getExpiredMedicines() {
        String sql = "SELECT * FROM medicines WHERE expiry_date < CURDATE()";
        return jdbcTemplate.query(sql, this::mapRowToMedicine);
    }

    private void batchUpdateStock() {
        String sql = "UPDATE medicines SET stock_quantity = stock_quantity + ? WHERE id = ?";
        jdbcTemplate.batchUpdate(sql,
            List.of(
                new Object[]{20, 1},
                new Object[]{30, 3}
            )
        );
    }

    private Medicine suggestMedicine(String problem) {
        String problemLower = problem.toLowerCase();
        List<Medicine> medicines = getAllMedicines();

        for (Medicine medicine : medicines) {
            String nameLower = medicine.getName().toLowerCase();
            String descLower = medicine.getDescription() != null ? medicine.getDescription().toLowerCase() : "";

            if ((problemLower.contains("pain") || problemLower.contains("fever")) && nameLower.contains("paracetamol")) {
                return medicine;
            } else if (problemLower.contains("headache") && nameLower.contains("aspirin")) {
                return medicine;
            } else if (problemLower.contains("inflammation") && nameLower.contains("ibuprofen")) {
                return medicine;
            } else if (problemLower.contains("cough") && nameLower.contains("cough syrup")) {
                return medicine;
            } else if (problemLower.contains("immunity") && nameLower.contains("vitamin c")) {
                return medicine;
            }
        }
        return null;
    }

    private Medicine mapRowToMedicine(ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Medicine(
            rs.getInt("id"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getBigDecimal("price"),
            rs.getInt("stock_quantity"),
            rs.getDate("expiry_date")
        );
    }
}