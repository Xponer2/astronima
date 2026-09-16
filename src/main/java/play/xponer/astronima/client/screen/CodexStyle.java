package play.xponer.astronima.client.screen;

/**
 * The codex's LSS stylesheet — every class name the other {@code Codex*} files draw with, in one
 * place. Split out of {@code CodexUi} (design/codex-rework.md §9 / C7) as its own concern for the
 * same reason the dead plan's own {@code CodexPalette} was: colour and panel styling changes on
 * its own schedule, unrelated to layout or interaction code, and finding it meant scrolling past
 * either to reach it.
 */
final class CodexStyle {
    static final String STYLE = """
            .codex_root {
              background: #0000009c;
            }
            .codex_book {
              background: sdf(#14181dee, 3, 1, #2a323a);
            }
            .codex_rail {
              background: #14181d00;
              padding-all: 8;
              gap-all: 4;
            }
            .codex_search {
              background: sdf(#0d1013, 2, 1, #2a323a);
              text-color: #c9d2da;
              height: 14;
              padding-all: 3;
            }
            .codex_section {
              text-color: #7e8894;
              font-size: 7;
            }
            .codex_page_btn {
              background: #14181d00;
              padding-all: 3;
              min-height: 14;
            }
            .codex_page_btn_hover {
              background: sdf(#22303c, 2);
            }
            .codex_page_btn_active {
              background: sdf(#22303c, 2);
              padding-all: 3;
              min-height: 14;
            }
            .codex_page_btn_active.codex_page_btn_hover {
              background: sdf(#28404f, 2);
            }
            .codex_title {
              text-color: #f2f6fa;
              font-size: 12;
            }
            .codex_heading {
              text-color: #f2f6fa;
              font-size: 9;
            }
            .codex_body {
              text-color: #c9d2da;
            }
            .codex_bullet {
              text-color: #c9d2da;
              margin-left: 8;
            }
            .codex_callout {
              background: sdf(#1b232c, 2, 1, #6fb0ee);
              padding-all: 6;
            }
            .codex_rule {
              background: #2a323a;
              height: 1;
            }
            .codex_locked_plate {
              background: sdf(#14181c, 2, 1, #4a4438);
              padding-all: 6;
            }
            .codex_table {
              background: sdf(#0d1013, 2, 1, #2a323a);
              padding-all: 4;
              gap-all: 3;
            }
            .codex_table_header {
              background: #1b232c;
              padding-all: 3;
              gap-all: 4;
            }
            .codex_table_row {
              padding-all: 3;
              gap-all: 4;
            }
            .codex_dim {
              text-color: #7e8894;
            }
            .codex_related_btn {
              background: sdf(#1b232c, 2, 1, #6fb0ee);
              padding-all: 3;
              min-height: 14;
            }
            .codex_related_btn_hover {
              background: sdf(#22303c, 2, 1, #6fb0ee);
            }
            .codex_recipe {
              background: sdf(#0d1013, 2, 1, #2a323a);
              padding-all: 8;
            }
            .codex_recipe_node {
              background: sdf(#1b232c, 2, 1, #2a323a);
              padding-all: 4;
            }
            .codex_recipe_node_result {
              background: sdf(#1b232c, 3, 1, #6fb0ee);
              padding-all: 5;
            }
            .codex_recipe_grid {
              background: sdf(#0d1013, 2, 1, #2a323a);
              padding-all: 3;
            }
            .codex_recipe_cell {
              background: sdf(#1b232c, 1, 1, #2a323a);
              padding-all: 1;
            }
            .codex_recipe_cell_empty {
              background: sdf(#0d1013, 1);
              padding-all: 1;
            }
            .codex_strip {
              background: #0d1013cc;
            }
            .codex_calc {
              background: sdf(#0d1013, 2, 1, #2a323a);
              padding-all: 8;
              gap-all: 6;
            }
            .codex_calc_input_row {
              padding-all: 2;
            }
            .codex_calc_output_row {
              background: sdf(#1b232c, 1, 1, #2a323a);
              padding-all: 3;
              gap-all: 4;
            }
            .codex_calc_good {
              background: sdf(#1b232c, 1, 1, #4cd964);
            }
            .codex_calc_marginal {
              background: sdf(#1b232c, 1, 1, #d9a24c);
            }
            .codex_calc_bad {
              background: sdf(#1b232c, 1, 1, #d9534c);
            }
            .codex_calc_impossible {
              background: sdf(#1b232c, 1, 1, #7e2a2a);
            }
            .codex_calc_info {
              background: sdf(#1b232c, 1, 1, #6fb0ee);
            }
            .codex_calc_error {
              background: sdf(#3a1414, 2, 1, #d9534c);
              padding-all: 6;
              text-color: #ffb4ac;
            }
            """;

    private CodexStyle() {}
}
