package demo;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.font.TextAttribute;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.*;

import static java.lang.Math.round;

// https://stackoverflow.com/questions/11146319/dynamically-changing-jtable-font-size
public class TestResizeWithPrepareRenderer extends JFrame {

    private static final long serialVersionUID = 1L;
    private JTable table;

    public TestResizeWithPrepareRenderer() {
        Object[] columnNames = {"Type", "Company", "Shares", "Price", "Boolean"};
        Object[][] data = {
                {"Buy", "IBM", 1000, 80.50, false},
                {"Sell", "MicroSoft", 2000, 6.25, true},
                {"Sell", "Apple", 3000, 7.35, true},
                {"Buy", "Nortel", 4000, 20.00, false}
        };
        DefaultTableModel model = new DefaultTableModel(data, columnNames) {
            @Override
            public Class getColumnClass(int column) {
                return getValueAt(0, column).getClass();
            }
        };

        table = new JTable(model) {
            private Border outside = new MatteBorder(1, 0, 1, 0, Color.red);
            private Border inside = new EmptyBorder(0, 1, 0, 1);
            private Border highlight = new CompoundBorder(outside, inside);

            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component comp = super.prepareRenderer(renderer, row, column);
                JComponent jc = (JComponent) comp;
                Map<TextAttribute, ?> attributes = (table.getFont()).getAttributes();
                // attributes.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
                // attributes.put(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON);
                if (!isRowSelected(row)) {
                    comp.setForeground(Color.black);
                    comp.setBackground(row % 2 == 0 ? Color.white : Color.orange);
                    int modelRow = convertRowIndexToModel(row);
                    String type = (String) getModel().getValueAt(modelRow, 0);
                    if (type.equals("Sell")) {
                        comp.setFont(new Font(attributes));
                        comp.setForeground(Color.red);
                    } else {
                        comp.setFont(table.getFont());
                    }
                } else {
                    comp.setFont(table.getFont());
                }
                jc.setBorder(BorderFactory.createCompoundBorder(jc.getBorder(), BorderFactory.createEmptyBorder(0, 0, 0, 5)));
                return comp;
            }
        };

        table.setPreferredScrollableViewportSize(table.getPreferredSize());
        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane);
        JButton increase = new JButton("+");
        increase.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Font font = table.getFont();
                font = font.deriveFont((float) (font.getSize2D() * 1.10));
                table.setFont(font);
                table.setRowHeight((int) (table.getRowHeight() * 1.10));
            }
        });
        add(increase, BorderLayout.EAST);
        JButton decrease = new JButton("-");
        decrease.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Font font = table.getFont();
                font = font.deriveFont((float) (font.getSize2D() * .90));
                table.setFont(font);
                table.setRowHeight((int) (table.getRowHeight() * .90));
            }
        });
        add(decrease, BorderLayout.WEST);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                TestResizeWithPrepareRenderer frame = new TestResizeWithPrepareRenderer();
                frame.setDefaultCloseOperation(EXIT_ON_CLOSE);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            }
        });
    }
}