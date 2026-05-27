import java.awt.*;
import java.awt.geom.*;
import java.io.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.*;
import javax.swing.*;
import javax.swing.table.*;

// ═══════════════════════════════════════════════════════════════════════════════
//  ExpenseIQ — Single-File Expense Analysis & Budgeting System
//  Run: javac ExpenseIQ.java && java ExpenseIQ
// ═══════════════════════════════════════════════════════════════════════════════

public class Expenseiq {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
            new LoginFrame().setVisible(true);
        });
    }

    // ══════════════════════════════════════════════════════
    //  PALETTE (shared across all UI classes)
    // ══════════════════════════════════════════════════════
    static final Color BG      = new Color(15,  17,  26);
    static final Color SIDEBAR = new Color(20,  23,  36);
    static final Color CARD    = new Color(24,  27,  42);
    static final Color ACCENT  = new Color(99,  179, 237);
    static final Color ACCENT2 = new Color(72,  149, 239);
    static final Color TEXT    = new Color(226, 232, 240);
    static final Color MUTED   = new Color(113, 128, 150);
    static final Color SUCCESS = new Color(72,  187, 120);
    static final Color DANGER  = new Color(252, 129, 129);
    static final Color WARNING = new Color(246, 173, 85);
    static final Color FIELD   = new Color(35,  40,  60);

    static final Color[] CAT_COLORS = {
        new Color(99,179,237), new Color(154,230,180), new Color(251,211,141),
        new Color(252,129,129), new Color(183,148,246), new Color(118,229,252),
        new Color(246,173,85),  new Color(160,174,192)
    };

    static final String[] MONTHS = {
        "January","February","March","April","May","June",
        "July","August","September","October","November","December"
    };

    // ══════════════════════════════════════════════════════
    //  MODEL — Expense
    // ══════════════════════════════════════════════════════
    enum Category { FOOD, TRANSPORT, SHOPPING, ENTERTAINMENT, HEALTH, EDUCATION, UTILITIES, OTHER }

    static class Expense implements Serializable {
        private static final long serialVersionUID = 1L;
        int id; String title, note; double amount; Category category; LocalDate date;

        Expense(int id, String title, double amount, Category category, LocalDate date, String note) {
            this.id=id; this.title=title; this.amount=amount;
            this.category=category; this.date=date; this.note=note;
        }
    }

    // ══════════════════════════════════════════════════════
    //  MODEL — User
    // ══════════════════════════════════════════════════════
    static class User implements Serializable {
        private static final long serialVersionUID = 1L;
        String username, passwordHash;
        Map<Category, Double> budgets = new EnumMap<>(Category.class);

        User(String username, String passwordHash) {
            this.username=username; this.passwordHash=passwordHash;
            for (Category c : Category.values()) budgets.put(c, 0.0);
        }
        double getBudget(Category c) { return budgets.getOrDefault(c, 0.0); }
        void   setBudget(Category c, double v) { budgets.put(c, v); }
    }

    // ══════════════════════════════════════════════════════
    //  DATA MANAGER — File Persistence
    // ══════════════════════════════════════════════════════
    static class DataManager {
        private static final String DIR      = "data/";
        private static final String USERS    = DIR + "users.dat";
        private static final String EXP_PFX  = DIR + "expenses_";

        DataManager() { new File(DIR).mkdirs(); }

        @SuppressWarnings("unchecked")
        Map<String, User> loadUsers() {
            File f = new File(USERS); if (!f.exists()) return new HashMap<>();
            try (ObjectInputStream o = new ObjectInputStream(new FileInputStream(f))) {
                return (Map<String,User>) o.readObject();
            } catch (Exception e) { return new HashMap<>(); }
        }

        void saveUsers(Map<String,User> users) {
            try (ObjectOutputStream o = new ObjectOutputStream(new FileOutputStream(USERS))) {
                o.writeObject(users);
            } catch (IOException e) { e.printStackTrace(); }
        }

        @SuppressWarnings("unchecked")
        List<Expense> loadExpenses(String username) {
            File f = new File(EXP_PFX + username + ".dat");
            if (!f.exists()) return new ArrayList<>();
            try (ObjectInputStream o = new ObjectInputStream(new FileInputStream(f))) {
                return (List<Expense>) o.readObject();
            } catch (Exception e) { return new ArrayList<>(); }
        }

        void saveExpenses(String username, List<Expense> list) {
            try (ObjectOutputStream o = new ObjectOutputStream(
                    new FileOutputStream(EXP_PFX + username + ".dat"))) {
                o.writeObject(list);
            } catch (IOException e) { e.printStackTrace(); }
        }

        static String hashPassword(String pw) {
            try {
                byte[] h = MessageDigest.getInstance("SHA-256").digest(pw.getBytes("UTF-8"));
                StringBuilder sb = new StringBuilder();
                for (byte b : h) sb.append(String.format("%02x", b));
                return sb.toString();
            } catch (Exception e) { return pw; }
        }
    }

    // ══════════════════════════════════════════════════════
    //  SERVICE — Business Logic
    // ══════════════════════════════════════════════════════
    static class ExpenseService {
        private final DataManager dm;
        private final String username;
        private List<Expense> expenses;
        private int nextId = 1;

        ExpenseService(DataManager dm, String username) {
            this.dm = dm; this.username = username;
            this.expenses = dm.loadExpenses(username);
            nextId = expenses.stream().mapToInt(e -> e.id).max().orElse(0) + 1;
        }

        void addExpense(String title, double amount, Category cat, LocalDate date, String note) {
            expenses.add(new Expense(nextId++, title, amount, cat, date, note));
            dm.saveExpenses(username, expenses);
        }

        boolean deleteExpense(int id) {
            boolean r = expenses.removeIf(e -> e.id == id);
            if (r) dm.saveExpenses(username, expenses);
            return r;
        }

        List<Expense> getByMonth(int year, int month) {
            return expenses.stream()
                .filter(e -> e.date.getYear()==year && e.date.getMonthValue()==month)
                .sorted(Comparator.comparing((Expense e)->e.date).reversed())
                .collect(Collectors.toList());
        }

        Map<Category, Double> categoryTotals(int year, int month) {
            Map<Category,Double> t = new EnumMap<>(Category.class);
            for (Category c : Category.values()) t.put(c, 0.0);
            getByMonth(year, month).forEach(e -> t.merge(e.category, e.amount, Double::sum));
            return t;
        }

        double monthTotal(int year, int month) {
            return getByMonth(year, month).stream().mapToDouble(e->e.amount).sum();
        }

        Map<String,Double> yearlyMonthly(int year) {
            Map<String,Double> m = new LinkedHashMap<>();
            String[] names = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
            for (int i=1;i<=12;i++) m.put(names[i-1], monthTotal(year, i));
            return m;
        }

        List<String> budgetAlerts(User user, int year, int month) {
            Map<Category,Double> totals = categoryTotals(year, month);
            List<String> alerts = new ArrayList<>();
            for (Category c : Category.values()) {
                double budget = user.getBudget(c), spent = totals.getOrDefault(c, 0.0);
                if (budget > 0 && spent > budget)
                    alerts.add(String.format("%s: spent ₹%.0f / budget ₹%.0f  (%.0f%% over)",
                            c, spent, budget, ((spent-budget)/budget)*100));
            }
            return alerts;
        }
    }

    // ══════════════════════════════════════════════════════
    //  UI HELPERS — shared static methods
    // ══════════════════════════════════════════════════════
    static JButton makeButton(String text, Color bg) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isPressed() ? bg.darker() : getModel().isRollover() ? bg.brighter() : bg);
                g2.fillRoundRect(0,0,getWidth(),getHeight(),10,10);
                g2.dispose(); super.paintComponent(g);
            }
        };
        b.setForeground(Color.WHITE); b.setFont(new Font("Segoe UI", Font.BOLD, 13));
        b.setOpaque(false); b.setContentAreaFilled(false);
        b.setBorderPainted(false); b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(9, 18, 9, 18));
        return b;
    }

    static JTextField styledField(String hint) {
        JTextField f = new JTextField();
        f.setBackground(FIELD); f.setForeground(TEXT); f.setCaretColor(ACCENT);
        f.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(99,179,237,70), 1),
            BorderFactory.createEmptyBorder(8,10,8,10)));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        f.putClientProperty("JTextField.placeholderText", hint);
        return f;
    }

    static JPasswordField styledPassword(String hint) {
        JPasswordField f = new JPasswordField();
        f.setBackground(FIELD); f.setForeground(TEXT); f.setCaretColor(ACCENT);
        f.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(99,179,237,70), 1),
            BorderFactory.createEmptyBorder(8,10,8,10)));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        f.putClientProperty("JTextField.placeholderText", hint);
        return f;
    }

    static <E> void styleCombo(JComboBox<E> c) {

    c.setBackground(Color.WHITE);
    c.setForeground(Color.BLACK);

    c.setFont(new Font("Segoe UI", Font.BOLD, 13));

    DefaultListCellRenderer renderer =
        new DefaultListCellRenderer();

    renderer.setBackground(Color.WHITE);
    renderer.setForeground(Color.BLACK);

    c.setRenderer(renderer);
}

    static JLabel label(String text, int size, boolean bold, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", bold ? Font.BOLD : Font.PLAIN, size));
        l.setForeground(color);
        return l;
    }

    static Component vgap(int h) { return Box.createRigidArea(new Dimension(0, h)); }

    // ══════════════════════════════════════════════════════
    //  LOGIN FRAME
    // ══════════════════════════════════════════════════════
    static class LoginFrame extends JFrame {
        private final DataManager dm = new DataManager();
        private final JTextField userField = styledField("Username");
        private final JPasswordField passField = styledPassword("Password");
        private final JLabel status = label(" ", 12, false, DANGER);

        LoginFrame() {
            setTitle("ExpenseIQ – Sign In");
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setSize(440, 560); setLocationRelativeTo(null); setResizable(false);

            JPanel root = new JPanel(new GridBagLayout());
            root.setBackground(BG);

            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBackground(CARD);
            card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(99,179,237,50), 1),
                BorderFactory.createEmptyBorder(40,40,40,40)));
            card.setPreferredSize(new Dimension(360, 480));

            JLabel logo = label("💰 ExpenseIQ", 26, true, ACCENT);
            logo.setAlignmentX(CENTER_ALIGNMENT);
            JLabel sub  = label("Smart Budget & Expense Tracker", 13, false, MUTED);
            sub.setAlignmentX(CENTER_ALIGNMENT);

            JButton loginBtn = makeButton("Sign In", ACCENT2);
            JButton regBtn   = makeButton("Create Account", new Color(56,161,105));
            loginBtn.setAlignmentX(CENTER_ALIGNMENT);
            regBtn.setAlignmentX(CENTER_ALIGNMENT);
            loginBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            regBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            status.setAlignmentX(CENTER_ALIGNMENT);

            loginBtn.addActionListener(e -> doLogin());
            regBtn.addActionListener(e -> doRegister());
            getRootPane().setDefaultButton(loginBtn);

            card.add(logo); card.add(vgap(4)); card.add(sub); card.add(vgap(32));
            card.add(fieldLabel("Username")); card.add(vgap(4)); card.add(userField); card.add(vgap(14));
            card.add(fieldLabel("Password")); card.add(vgap(4)); card.add(passField); card.add(vgap(24));
            card.add(loginBtn); card.add(vgap(10)); card.add(regBtn); card.add(vgap(14));
            card.add(status);

            root.add(card);
            setContentPane(root);
        }

        private JLabel fieldLabel(String t) {
            JLabel l = label(t, 11, true, MUTED); l.setAlignmentX(LEFT_ALIGNMENT); return l;
        }

        private void doLogin() {
            String u = userField.getText().trim(), p = new String(passField.getPassword());
            if (u.isEmpty()||p.isEmpty()) { setStatus("Please fill all fields.", false); return; }
            Map<String,User> users = dm.loadUsers();
            User user = users.get(u);
            if (user==null || !user.passwordHash.equals(DataManager.hashPassword(p))) {
                setStatus("Invalid username or password.", false); return;
            }
            dispose();
            new DashboardFrame(user, dm).setVisible(true);
        }

        private void doRegister() {
            String u = userField.getText().trim(), p = new String(passField.getPassword());
            if (u.isEmpty()||p.isEmpty()) { setStatus("Please fill all fields.", false); return; }
            if (p.length()<4) { setStatus("Password must be 4+ characters.", false); return; }
            Map<String,User> users = dm.loadUsers();
            if (users.containsKey(u)) { setStatus("Username already taken.", false); return; }
            users.put(u, new User(u, DataManager.hashPassword(p)));
            dm.saveUsers(users);
            setStatus("Account created! You can sign in now.", true);
        }

        private void setStatus(String msg, boolean ok) {
            status.setForeground(ok ? SUCCESS : DANGER); status.setText(msg);
        }
    }

    // ══════════════════════════════════════════════════════
    //  DASHBOARD FRAME
    // ══════════════════════════════════════════════════════
    static class DashboardFrame extends JFrame {
        private final User user;
        private final DataManager dm;
        private final ExpenseService svc;
        private CardLayout cardLayout;
        private JPanel contentPanel;
        private ExpensesPanel expPanel;
        private AnalysisPanel anaPanel;
        private BudgetPanel   budPanel;

        DashboardFrame(User user, DataManager dm) {
            this.user=user; this.dm=dm;
            this.svc = new ExpenseService(dm, user.username);
            setTitle("ExpenseIQ – " + user.username);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setSize(1100, 720); setMinimumSize(new Dimension(900,600));
            setLocationRelativeTo(null);

            JPanel root = new JPanel(new BorderLayout());
            root.setBackground(BG);
            root.add(buildSidebar(), BorderLayout.WEST);
            root.add(buildContent(), BorderLayout.CENTER);
            setContentPane(root);
            show("expenses");
        }

        private JPanel buildSidebar() {
            JPanel s = new JPanel();
            s.setLayout(new BoxLayout(s, BoxLayout.Y_AXIS));
            s.setBackground(SIDEBAR); s.setPreferredSize(new Dimension(220,0));
            s.setBorder(BorderFactory.createEmptyBorder(24,16,24,16));

            JLabel brand = label("💰 ExpenseIQ", 20, true, ACCENT);
            brand.setAlignmentX(LEFT_ALIGNMENT);
            JLabel userLbl = label("@"+user.username, 12, false, MUTED);
            userLbl.setAlignmentX(LEFT_ALIGNMENT);

            s.add(brand); s.add(vgap(4)); s.add(userLbl); s.add(vgap(28));
            s.add(sectionLabel("NAVIGATION")); s.add(vgap(8));
            s.add(navBtn("📋  Expenses",  "expenses"));
            s.add(vgap(6));
            s.add(navBtn("📊  Analysis",  "analysis"));
            s.add(vgap(6));
            s.add(navBtn("🎯  Budget",    "budget"));
            s.add(Box.createVerticalGlue());

            JButton logout = navButton("⟵  Logout");
            logout.setForeground(DANGER);
            logout.addActionListener(e -> { dispose(); new LoginFrame().setVisible(true); });
            s.add(logout);
            return s;
        }

        private JButton navBtn(String text, String section) {
            JButton b = navButton(text);
            b.addActionListener(e -> show(section));
            return b;
        }

        private JButton navButton(String text) {
            JButton b = new JButton(text);
            b.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            b.setForeground(TEXT); b.setBackground(SIDEBAR);
            b.setOpaque(true); b.setBorderPainted(false); b.setFocusPainted(false);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.setAlignmentX(LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setBorder(BorderFactory.createEmptyBorder(8,12,8,12));
            return b;
        }

        private JLabel sectionLabel(String t) {
            JLabel l = label(t, 10, true, MUTED); l.setAlignmentX(LEFT_ALIGNMENT); return l;
        }

        private JPanel buildContent() {
            cardLayout = new CardLayout();
            contentPanel = new JPanel(cardLayout);
            contentPanel.setBackground(BG);
            expPanel = new ExpensesPanel(user, svc, dm, this);
            anaPanel = new AnalysisPanel(svc);
            budPanel = new BudgetPanel(user, dm, svc);
            contentPanel.add(expPanel, "expenses");
            contentPanel.add(anaPanel, "analysis");
            contentPanel.add(budPanel, "budget");
            return contentPanel;
        }

        void show(String name) {
            cardLayout.show(contentPanel, name);
            if (name.equals("analysis")) anaPanel.refresh();
            if (name.equals("budget"))   budPanel.refresh();
            if (name.equals("expenses")) expPanel.refresh();
        }
    }

    // ══════════════════════════════════════════════════════
    //  EXPENSES PANEL
    // ══════════════════════════════════════════════════════
    static class ExpensesPanel extends JPanel {
        private final User user;
        private final ExpenseService svc;
        private final DataManager dm;
        private final DashboardFrame parent;
        private DefaultTableModel tableModel;
        private JTable table;
        private JLabel totalLabel;
        private JComboBox<String> monthCombo;
        private JComboBox<Integer> yearCombo;

        ExpensesPanel(User user, ExpenseService svc, DataManager dm, DashboardFrame parent) {
            this.user=user; this.svc=svc; this.dm=dm; this.parent=parent;
            setBackground(BG); setLayout(new BorderLayout()); buildUI();
        }

        private void buildUI() {
            // Header
            JPanel header = new JPanel(new BorderLayout());
            header.setBackground(BG); header.setBorder(BorderFactory.createEmptyBorder(24,24,12,24));
            header.add(label("Expenses",24,true,TEXT), BorderLayout.WEST);

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));
            controls.setOpaque(false);
            monthCombo = new JComboBox<>(MONTHS);
            monthCombo.setSelectedIndex(LocalDate.now().getMonthValue()-1); styleCombo(monthCombo);
            yearCombo = new JComboBox<>();
            int y = LocalDate.now().getYear();
            for (int i=y-3;i<=y+1;i++) yearCombo.addItem(i);
            yearCombo.setSelectedItem(y); styleCombo(yearCombo);
            JButton flt = makeButton("Filter", ACCENT);
            flt.addActionListener(e -> loadExpenses());
            JButton add = makeButton("+ Add Expense", new Color(56,161,105));
            add.addActionListener(e -> openAddDialog());
            controls.add(mlabel("Month:")); controls.add(monthCombo);
            controls.add(mlabel("Year:"));  controls.add(yearCombo);
            controls.add(flt); controls.add(add);
            header.add(controls, BorderLayout.EAST);

            // Table
            String[] cols = {"ID","Title","Amount (₹)","Category","Date","Note"};
            tableModel = new DefaultTableModel(cols,0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            table = new JTable(tableModel);
            styleTable(table);
            JScrollPane sp = new JScrollPane(table);
            sp.setBackground(CARD); sp.getViewport().setBackground(CARD);
            sp.setBorder(BorderFactory.createEmptyBorder());
            JPanel tableCard = new JPanel(new BorderLayout());
            tableCard.setBackground(CARD);
            tableCard.setBorder(BorderFactory.createEmptyBorder(0,24,0,24));
            tableCard.add(sp);

            // Footer
            JPanel footer = new JPanel(new BorderLayout());
            footer.setBackground(BG); footer.setBorder(BorderFactory.createEmptyBorder(12,24,16,24));
            totalLabel = label("Total: ₹0.00", 16, true, ACCENT);
            JButton del = makeButton("Delete Selected", DANGER);
            del.addActionListener(e -> deleteSelected());
            footer.add(totalLabel, BorderLayout.WEST); footer.add(del, BorderLayout.EAST);

            add(header, BorderLayout.NORTH);
            add(tableCard, BorderLayout.CENTER);
            add(footer, BorderLayout.SOUTH);
            loadExpenses();
        }

        public void refresh() { loadExpenses(); }

        private void loadExpenses() {
            tableModel.setRowCount(0);
            int month = monthCombo.getSelectedIndex()+1, year=(Integer)yearCombo.getSelectedItem();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy");
            svc.getByMonth(year, month).forEach(e -> tableModel.addRow(new Object[]{
                e.id, e.title, String.format("%.2f",e.amount), e.category, e.date.format(fmt), e.note
            }));
            totalLabel.setText(String.format("Total: ₹%.2f", svc.monthTotal(year, month)));
        }

        private void deleteSelected() {
            int row = table.getSelectedRow();
            if (row==-1) { JOptionPane.showMessageDialog(this,"Select an expense to delete."); return; }
            int id = (Integer)tableModel.getValueAt(row,0);
            if (JOptionPane.showConfirmDialog(this,"Delete this expense?","Confirm",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                svc.deleteExpense(id); loadExpenses();
            }
        }

        private void openAddDialog() {
            JDialog d = new JDialog(parent, "Add Expense", true);
            d.setSize(420,410); d.setLocationRelativeTo(parent);
            JPanel p = new JPanel(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setBackground(CARD); p.setBorder(BorderFactory.createEmptyBorder(24,24,24,24));

            JTextField titleF  = styledField("e.g. Lunch");
            JTextField amountF = styledField("e.g. 250.00");
            JTextField noteF   = styledField("Optional note");
            JComboBox<Category> catC = new JComboBox<>(Category.values()); styleCombo(catC);
            JTextField dateF = styledField(LocalDate.now().toString());

            p.add(dlbl("Title"));    p.add(vgap(4)); p.add(titleF);  p.add(vgap(12));
            p.add(dlbl("Amount"));   p.add(vgap(4)); p.add(amountF); p.add(vgap(12));
            p.add(dlbl("Category")); p.add(vgap(4)); p.add(catC);    p.add(vgap(12));
            p.add(dlbl("Date (YYYY-MM-DD)")); p.add(vgap(4)); p.add(dateF); p.add(vgap(12));
            p.add(dlbl("Note"));     p.add(vgap(4)); p.add(noteF);   p.add(vgap(20));

            JButton save = makeButton("Save Expense", new Color(56,161,105));
            save.setAlignmentX(CENTER_ALIGNMENT);
            save.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            save.addActionListener(e -> {
                try {
                    String t = titleF.getText().trim();
                    if (t.isEmpty()) throw new Exception("Title is required.");
                    double amt = Double.parseDouble(amountF.getText().trim());
                    if (amt<=0) throw new Exception("Amount must be positive.");
                    Category cat = (Category)catC.getSelectedItem();
                    LocalDate date = LocalDate.parse(dateF.getText().trim());
                    svc.addExpense(t, amt, cat, date, noteF.getText().trim());
                    d.dispose(); loadExpenses();
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(d,"Invalid amount — enter a number.");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(d, ex.getMessage());
                }
            });
            p.add(save);
            d.setContentPane(p); d.setVisible(true);
        }

        private void styleTable(JTable t) {
            t.setBackground(CARD); t.setForeground(TEXT);
            t.setFont(new Font("Segoe UI", Font.PLAIN, 13)); t.setRowHeight(36);
            t.setGridColor(new Color(40,45,65));
            t.setSelectionBackground(new Color(99,179,237,60)); t.setSelectionForeground(TEXT);
            t.setShowHorizontalLines(true); t.setShowVerticalLines(false);
            JTableHeader h = t.getTableHeader();
            h.setBackground(new Color(30,34,52)); h.setForeground(MUTED);
            h.setFont(new Font("Segoe UI", Font.BOLD, 12));
            int[] w={40,160,100,120,120,160};
            for (int i=0;i<w.length&&i<t.getColumnCount();i++)
                t.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
        }

        private JLabel mlabel(String t) { return label(t,12,false,MUTED); }
        private JLabel dlbl(String t) { JLabel l=label(t,11,true,MUTED); l.setAlignmentX(LEFT_ALIGNMENT); return l; }
    }

    // ══════════════════════════════════════════════════════
    //  ANALYSIS PANEL — Pie Chart + Bar Chart + Stats
    // ══════════════════════════════════════════════════════
    static class AnalysisPanel extends JPanel {
        private final ExpenseService svc;
        private int selMonth = LocalDate.now().getMonthValue();
        private int selYear  = LocalDate.now().getYear();
        private JComboBox<String> monthCombo;
        private JComboBox<Integer> yearCombo;
        private PieChart pie;
        private BarChart bar;
        private JPanel stats;

        AnalysisPanel(ExpenseService svc) {
            this.svc=svc; setBackground(BG); setLayout(new BorderLayout()); buildUI();
        }

        private void buildUI() {
            // Header
            JPanel header = new JPanel(new BorderLayout());
            header.setBackground(BG); header.setBorder(BorderFactory.createEmptyBorder(24,24,12,24));
            header.add(label("Analysis & Reports",24,true,TEXT), BorderLayout.WEST);
            JPanel fr = new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));
            fr.setOpaque(false);
            monthCombo=new JComboBox<>(MONTHS); monthCombo.setSelectedIndex(selMonth-1); styleCombo(monthCombo);
            yearCombo=new JComboBox<>();
            int y=LocalDate.now().getYear();
            for (int i=y-3;i<=y+1;i++) yearCombo.addItem(i);
            yearCombo.setSelectedItem(y); styleCombo(yearCombo);
            JButton apply=makeButton("Apply",ACCENT);
            apply.addActionListener(e->{selMonth=monthCombo.getSelectedIndex()+1;selYear=(Integer)yearCombo.getSelectedItem();refresh();});
            fr.add(label("Month:",12,false,MUTED)); fr.add(monthCombo);
            fr.add(label("Year:",12,false,MUTED));  fr.add(yearCombo); fr.add(apply);
            header.add(fr, BorderLayout.EAST);

            // Charts
            JPanel charts = new JPanel(new GridLayout(1,2,16,0));
            charts.setBackground(BG); charts.setBorder(BorderFactory.createEmptyBorder(0,24,16,24));
            pie = new PieChart(); bar = new BarChart();
            charts.add(chartCard(pie, "Category Breakdown"));
            charts.add(chartCard(bar, "Monthly Spending"));

            // Stats
            stats = new JPanel(new GridLayout(1,4,16,0));
            stats.setBackground(BG); stats.setBorder(BorderFactory.createEmptyBorder(0,24,24,24));

            add(header, BorderLayout.NORTH);
            add(charts,  BorderLayout.CENTER);
            add(stats,   BorderLayout.SOUTH);
            refresh();
        }

        public void refresh() {
            Map<Category,Double> ct = svc.categoryTotals(selYear, selMonth);
            pie.setData(ct);
            bar.setData(svc.yearlyMonthly(selYear));

            stats.removeAll();
            double total = svc.monthTotal(selYear, selMonth);
            long   count = svc.getByMonth(selYear, selMonth).size();
            double avg   = count>0 ? total/count : 0;
            Category top = ct.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
            stats.add(statCard("Total Spent",    String.format("₹%.0f",total), ACCENT));
            stats.add(statCard("Transactions",   String.valueOf(count),         WARNING));
            stats.add(statCard("Avg per Entry",  String.format("₹%.0f",avg),   SUCCESS));
            stats.add(statCard("Top Category",   top!=null?top.name():"—",     DANGER));
            stats.revalidate(); stats.repaint(); repaint();
        }

        private JPanel chartCard(JPanel inner, String title) {
            JPanel c=new JPanel(new BorderLayout()); c.setBackground(CARD);
            c.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
            JLabel l=label(title,14,true,TEXT); l.setBorder(BorderFactory.createEmptyBorder(0,0,10,0));
            c.add(l, BorderLayout.NORTH); c.add(inner, BorderLayout.CENTER); return c;
        }

        private JPanel statCard(String lbl, String val, Color accent) {
            JPanel c=new JPanel(); c.setLayout(new BoxLayout(c,BoxLayout.Y_AXIS));
            c.setBackground(CARD); c.setBorder(BorderFactory.createEmptyBorder(16,20,16,20));
            JLabel vl=label(val,22,true,accent); vl.setAlignmentX(CENTER_ALIGNMENT);
            JLabel ll=label(lbl,12,false,MUTED);  ll.setAlignmentX(CENTER_ALIGNMENT);
            c.add(Box.createVerticalGlue()); c.add(vl); c.add(vgap(4)); c.add(ll); c.add(Box.createVerticalGlue());
            return c;
        }
    }

    // ── Pie Chart ─────────────────────────────────────────
    static class PieChart extends JPanel {
        private Map<Category,Double> data = new EnumMap<>(Category.class);

        PieChart() { setOpaque(false); setPreferredSize(new Dimension(300,240)); }

        void setData(Map<Category,Double> d) { this.data=d; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double total = data.values().stream().mapToDouble(Double::doubleValue).sum();
            if (total==0) { g2.setColor(MUTED); g2.setFont(new Font("Segoe UI",Font.PLAIN,13));
                g2.drawString("No data for this month", getWidth()/2-70, getHeight()/2); g2.dispose(); return; }
            int size=Math.min(getWidth()-130, getHeight()-20), x=10, y=(getHeight()-size)/2;
            double start=0; Category[] cats=Category.values();
            for (int i=0;i<cats.length;i++) {
                double v=data.getOrDefault(cats[i],0.0); if(v==0) continue;
                double arc=(v/total)*360;
                g2.setColor(CAT_COLORS[i]);
                g2.fill(new Arc2D.Double(x,y,size,size,start,arc,Arc2D.PIE));
                g2.setColor(CARD); g2.setStroke(new BasicStroke(2));
                g2.draw(new Arc2D.Double(x,y,size,size,start,arc,Arc2D.PIE));
                start+=arc;
            }
            int lx=size+22, ly=20; g2.setFont(new Font("Segoe UI",Font.PLAIN,10));
            for (int i=0;i<cats.length;i++) {
                double v=data.getOrDefault(cats[i],0.0); if(v==0) continue;
                g2.setColor(CAT_COLORS[i]); g2.fillRoundRect(lx,ly,12,12,4,4);
                g2.setColor(TEXT); g2.drawString(cats[i].name()+" ₹"+String.format("%.0f",v), lx+16, ly+11);
                ly+=20;
            }
            g2.dispose();
        }
    }

    // ── Bar Chart ─────────────────────────────────────────
    static class BarChart extends JPanel {
        private Map<String,Double> data = new LinkedHashMap<>();

        BarChart() { setOpaque(false); setPreferredSize(new Dimension(300,240)); }

        void setData(Map<String,Double> d) { this.data=d; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (data.isEmpty()) return;
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int pad=28, bottom=getHeight()-28, chartW=getWidth()-pad*2;
            double maxVal=data.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);
            if (maxVal==0) maxVal=1;
            List<Map.Entry<String,Double>> entries=new ArrayList<>(data.entrySet());
            int n=entries.size(), barW=(chartW/n)-6;
            for (int i=0;i<n;i++) {
                double v=entries.get(i).getValue();
                int bh=(int)((v/maxVal)*(bottom-36)), bx=pad+i*(chartW/n)+3, by=bottom-bh;
                g2.setPaint(new GradientPaint(bx,by,ACCENT,bx,bottom,ACCENT2));
                g2.fillRoundRect(bx,by,barW,bh,6,6);
                g2.setColor(MUTED); g2.setFont(new Font("Segoe UI",Font.PLAIN,9));
                g2.drawString(entries.get(i).getKey(), bx, bottom+14);
                if (v>0) { g2.setColor(TEXT); g2.setFont(new Font("Segoe UI",Font.BOLD,9));
                    g2.drawString("₹"+String.format("%.0f",v), bx, by-4); }
            }
            g2.setColor(new Color(60,70,90));
            g2.drawLine(pad, bottom, getWidth()-pad, bottom);
            g2.dispose();
        }
    }

    // ══════════════════════════════════════════════════════
    //  BUDGET PANEL
    // ══════════════════════════════════════════════════════
    static class BudgetPanel extends JPanel {
        private final User user;
        private final DataManager dm;
        private final ExpenseService svc;
        private final Map<Category,JTextField> fields = new EnumMap<>(Category.class);
        private JPanel alertsPanel;
        private JPanel budgetGrid;

        BudgetPanel(User user, DataManager dm, ExpenseService svc) {
            this.user=user; this.dm=dm; this.svc=svc;
            setBackground(BG); setLayout(new BorderLayout()); buildUI();
        }

        private void buildUI() {
            JPanel header=new JPanel(new BorderLayout());
            header.setBackground(BG); header.setBorder(BorderFactory.createEmptyBorder(24,24,12,24));
            header.add(label("Budget Manager",24,true,TEXT), BorderLayout.WEST);

            JPanel scroll=new JPanel();
            scroll.setLayout(new BoxLayout(scroll, BoxLayout.Y_AXIS));
            scroll.setBackground(BG); scroll.setBorder(BorderFactory.createEmptyBorder(0,24,24,24));

            budgetGrid=new JPanel(new GridLayout(0,2,16,16));
            budgetGrid.setBackground(BG);

            int m=LocalDate.now().getMonthValue(), y=LocalDate.now().getYear();
            Map<Category,Double> spent=svc.categoryTotals(y,m);
            for (Category c : Category.values()) budgetGrid.add(budgetCard(c, user.getBudget(c), spent.getOrDefault(c,0.0)));

            JButton save=makeButton("💾  Save All Budgets", new Color(56,161,105));
            save.setAlignmentX(CENTER_ALIGNMENT); save.setMaximumSize(new Dimension(Integer.MAX_VALUE,44));
            save.addActionListener(e->saveBudgets());

            JLabel alertTitle=label("⚠  Budget Alerts (This Month)",16,true,WARNING);
            alertTitle.setAlignmentX(LEFT_ALIGNMENT);

            alertsPanel=new JPanel(); alertsPanel.setLayout(new BoxLayout(alertsPanel,BoxLayout.Y_AXIS));
            alertsPanel.setBackground(BG); alertsPanel.setAlignmentX(LEFT_ALIGNMENT);

            scroll.add(budgetGrid); scroll.add(vgap(20)); scroll.add(save);
            scroll.add(vgap(28)); scroll.add(alertTitle); scroll.add(vgap(12)); scroll.add(alertsPanel);

            JScrollPane sp=new JScrollPane(scroll);
            sp.setBackground(BG); sp.getViewport().setBackground(BG);
            sp.setBorder(BorderFactory.createEmptyBorder());

            add(header, BorderLayout.NORTH); add(sp, BorderLayout.CENTER);
            refreshAlerts();
        }

        private JPanel budgetCard(Category cat, double budget, double spent) {
            JPanel card=new JPanel(new BorderLayout(0,8));
            card.setBackground(CARD); card.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
            card.add(label(catIcon(cat)+"  "+cat.name(), 14, true, TEXT), BorderLayout.NORTH);

            JTextField f=styledField(String.format("%.0f",budget));
            f.setText(String.format("%.0f",budget));
            fields.put(cat,f);
            JPanel frow=new JPanel(new BorderLayout(6,0)); frow.setOpaque(false);
            frow.add(label("₹",13,false,MUTED), BorderLayout.WEST); frow.add(f, BorderLayout.CENTER);

            double pct=budget>0?Math.min(spent/budget,1.0):0;
            Color bc=pct>=1?DANGER:pct>=0.75?WARNING:SUCCESS;
            JPanel prog=new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    super.paintComponent(g); Graphics2D g2=(Graphics2D)g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(40,46,66)); g2.fillRoundRect(0,0,getWidth(),getHeight(),6,6);
                    g2.setColor(bc); g2.fillRoundRect(0,0,(int)(getWidth()*pct),getHeight(),6,6);
                    g2.dispose();
                }
            };
            prog.setOpaque(false); prog.setPreferredSize(new Dimension(0,8));
            JPanel bottom=new JPanel(new BorderLayout(0,4)); bottom.setOpaque(false);
            bottom.add(prog, BorderLayout.NORTH);
            bottom.add(label(String.format("Spent ₹%.0f / Budget ₹%.0f",spent,budget),11,false,MUTED), BorderLayout.SOUTH);

            card.add(frow, BorderLayout.CENTER); card.add(bottom, BorderLayout.SOUTH);
            return card;
        }

        private void saveBudgets() {
            for (Category c : Category.values()) {
                JTextField f=fields.get(c); if(f==null) continue;
                try { user.setBudget(c, Double.parseDouble(f.getText().trim())); }
                catch (NumberFormatException ignored) {}
            }
            Map<String,User> users=dm.loadUsers();
            users.put(user.username, user);
            dm.saveUsers(users);
            refreshAlerts();
            JOptionPane.showMessageDialog(this,"Budgets saved successfully!","Saved",JOptionPane.INFORMATION_MESSAGE);
        }

        public void refresh() {
            removeAll(); buildUI(); revalidate(); repaint();
        }

        private void refreshAlerts() {
            if (alertsPanel==null) return;
            alertsPanel.removeAll();
            int m=LocalDate.now().getMonthValue(), y=LocalDate.now().getYear();
            List<String> alerts=svc.budgetAlerts(user,y,m);
            if (alerts.isEmpty()) {
                alertsPanel.add(label("✅  All categories are within budget!",13,false,SUCCESS));
            } else {
                for (String a : alerts) {
                    JPanel row=new JPanel(new BorderLayout());
                    row.setBackground(new Color(252,129,129,18));
                    row.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(252,129,129,55),1),
                        BorderFactory.createEmptyBorder(10,14,10,14)));
                    row.add(label("⚠  "+a,13,false,DANGER));
                    row.setMaximumSize(new Dimension(Integer.MAX_VALUE,44));
                    alertsPanel.add(row); alertsPanel.add(vgap(6));
                }
            }
            alertsPanel.revalidate(); alertsPanel.repaint();
        }

        private String catIcon(Category c) {
            return switch(c) {
                case FOOD->"🍔"; case TRANSPORT->"🚗"; case SHOPPING->"🛍";
                case ENTERTAINMENT->"🎬"; case HEALTH->"💊"; case EDUCATION->"📚";
                case UTILITIES->"💡"; case OTHER->"📦";
            };
        }
    }

} // end class ExpenseIQ
