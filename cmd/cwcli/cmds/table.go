package cmds

import (
	"io"
	"strings"

	"github.com/cloudway/platform/cmd/cwcli/cmds/ansi"
)

type Table struct {
	titles []string
	rows   [][]string
	colors []*ansi.Color
}

func NewTable(titles ...string) *Table {
	return &Table{
		titles: titles,
	}
}

func (tab *Table) SetColor(i int, color *ansi.Color) {
	if tab.colors == nil {
		tab.colors = make([]*ansi.Color, len(tab.titles))
	}
	tab.colors[i] = color
}

func (tab *Table) AddRow(row ...string) {
	tab.rows = append(tab.rows, row)
}

func (tab *Table) AddSubtitle(subtitle string) {
	tab.rows = append(tab.rows, []string{subtitle})
}

func (tab *Table) Display(out io.Writer, gap int) {
	// calculate column widths
	widths := make([]int, len(tab.titles))
	for i, t := range tab.titles {
		widths[i] = len(t)
	}
	for _, row := range tab.rows {
		if len(row) != 1 {
			for i, cell := range row {
				if len(cell) > widths[i] {
					widths[i] = len(cell)
				}
			}
		}
	}

	var printHeader = true
	for i, row := range tab.rows {
		if len(row) == 1 {
			// for a single cell row, treat this row as a subtitle
			if i != 0 {
				io.WriteString(out, "\n")
			}
			io.WriteString(out, row[0])
			io.WriteString(out, "\n")
			printHeader = true
		} else {
			if printHeader {
				printHeader = false
				io.WriteString(out, tab.padding(widths, tab.titles, gap, true))
			}
			io.WriteString(out, tab.padding(widths, row, gap, false))
		}
	}
}

func (tab *Table) padding(widths []int, cells []string, gap int, header bool) string {
	for i, w := range widths {
		if i != len(widths)-1 {
			pad := w - len(cells[i]) + gap
			cells[i] += strings.Repeat(" ", pad)
		}
		if !header && tab.colors != nil && tab.colors[i] != nil {
			cells[i] = tab.colors[i].Wrap(cells[i])
		}
	}

	row := strings.Join(cells, "")
	if header {
		row = ansi.Hilite(row)
	}
	return row + "\n"
}
