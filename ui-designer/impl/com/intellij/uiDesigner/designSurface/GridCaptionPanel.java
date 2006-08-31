/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.designSurface;

import com.intellij.ide.DeleteProvider;
import com.intellij.ide.dnd.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.LightColors;
import com.intellij.uiDesigner.CaptionSelection;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.GridChangeUtil;
import com.intellij.uiDesigner.componentTree.ComponentSelectionListener;
import com.intellij.uiDesigner.radComponents.RadAbstractGridLayoutManager;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

/**
 * @author yole
 */
public class GridCaptionPanel extends JPanel implements ComponentSelectionListener, DataProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.designSurface.GridCaptionPanel");

  private GuiEditor myEditor;
  private boolean myIsRow;
  private RadContainer mySelectedContainer;
  private DefaultListSelectionModel mySelectionModel = new DefaultListSelectionModel();
  private int myResizeLine = -1;
  private int myDropInsertLine = -1;
  private LineFeedbackPainter myFeedbackPainter = new LineFeedbackPainter();
  private DeleteProvider myDeleteProvider = new MyDeleteProvider();
  private Alarm myAlarm = new Alarm();

  public GridCaptionPanel(final GuiEditor editor, final boolean isRow) {
    myEditor = editor;
    myIsRow = isRow;
    mySelectionModel.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    mySelectionModel.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        repaint();
        myEditor.fireSelectedComponentChanged();
      }
    });
    setBackground(Color.LIGHT_GRAY);
    editor.addComponentSelectionListener(this);

    final MyMouseListener listener = new MyMouseListener();
    addMouseListener(listener);
    addMouseMotionListener(listener);
    addKeyListener(new MyKeyListener());
    setFocusable(true);

    DnDManager.getInstance(editor.getProject()).registerSource(new MyDnDSource(), this);
    DnDManager.getInstance(editor.getProject()).registerTarget(new MyDnDTarget(), this);

    addFocusListener(new FocusAdapter() {
      @Override public void focusGained(FocusEvent e) {
        repaint();
        // ensure we don't have two repaints of properties panel - one from focus gain and another from click
        myAlarm.addRequest(new Runnable() {
          public void run() {
            editor.fireSelectedComponentChanged();
          }
        }, 1000);
      }

      @Override public void focusLost(FocusEvent e) {
        repaint();
      }
    });
  }

  public RadContainer getSelectedContainer() {
    return mySelectedContainer;
  }

  public boolean isRow() {
    return myIsRow;
  }

  @Override public Dimension getPreferredSize() {
    return new Dimension(16, 16);
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override public void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2d = (Graphics2D) g;

    final Rectangle bounds = getBounds();
    final int paintedSize = 8;
    final int paintOffset = 7;

    RadContainer container = getSelectedGridContainer();
    if (container == null) {
      return;
    }
    RadAbstractGridLayoutManager layout = container.getGridLayoutManager();
    int[] coords = layout.getGridCellCoords(container, myIsRow);
    int[] sizes = layout.getGridCellSizes(container, myIsRow);
    int count = myIsRow ? layout.getGridRowCount(container) : layout.getGridColumnCount(container);

    for(int i=0; i<count; i++) {
      int x = myIsRow ? 0 : coords [i];
      int y = myIsRow ? coords [i] : 0;
      Point pnt = SwingUtilities.convertPoint(container.getDelegee(), x, y, this);

      Rectangle rc = myIsRow
                     ? new Rectangle(bounds.x+paintOffset, pnt.y, paintedSize, sizes [i])
                     : new Rectangle(pnt.x, bounds.y+paintOffset, sizes [i], paintedSize);

      g.setColor(getCaptionColor(i));
      g.fillRect(rc.x, rc.y, rc.width, rc.height);

      Rectangle rcDecoration = myIsRow
                               ? new Rectangle(bounds.x, pnt.y, bounds.width, sizes [i])
                               : new Rectangle(pnt.x, bounds.y, sizes [i], bounds.height);
      layout.paintCaptionDecoration(container, myIsRow, i, g2d, rcDecoration);

      Stroke oldStroke = g2d.getStroke();
      int deltaX = 0;
      int deltaY = 0;
      if (isFocusOwner() && i == mySelectionModel.getLeadSelectionIndex()) {
        g.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(2.0f));
        deltaX = myIsRow ? 1 : 0;
        deltaY = myIsRow ? 0 : 1;
      }
      else {
        g.setColor(Color.DARK_GRAY);
      }
      g.drawRect(rc.x + deltaX, rc.y + deltaY, rc.width - deltaX, rc.height - deltaY);
      g2d.setStroke(oldStroke);
    }

    g.setColor(Color.DARK_GRAY);
    if (myIsRow) {
      g.drawLine(paintOffset+paintedSize, 0, paintOffset+paintedSize, bounds.height);
    }
    else {
      g.drawLine(0, paintOffset+paintedSize, bounds.width, paintOffset+paintedSize);
    }

    if (myDropInsertLine >= 0) {
      int[] lines = myIsRow ? layout.getHorizontalGridLines(container) : layout.getVerticalGridLines(container);
      int coord = lines [myDropInsertLine];
      if (myIsRow) {
        coord = SwingUtilities.convertPoint(container.getDelegee(), 0, coord, this).y;
      }
      else {
        coord = SwingUtilities.convertPoint(container.getDelegee(), coord, 0, this).x;
      }
      Stroke oldStroke = g2d.getStroke();
      g2d.setStroke(new BasicStroke(2.0f));
      g.setColor(Color.BLUE);
      if (myIsRow) {
        g.drawLine(bounds.x+1, coord, bounds.x+bounds.width-1, coord);
      }
      else {
        g.drawLine(coord, bounds.y+1, coord, bounds.y+bounds.height-1);
      }

      g2d.setStroke(oldStroke);
    }

  }

  private Color getCaptionColor(final int i) {
    if (mySelectionModel.isSelectedIndex(i)) {
      return LightColors.BLUE;
    }
    if (GridChangeUtil.canDeleteCell(mySelectedContainer, i, myIsRow, false)) {
      return Color.PINK;
    }
    return LightColors.GREEN;
  }

  @Nullable private RadContainer getSelectedGridContainer() {
    final ArrayList<RadComponent> selection = FormEditingUtil.getSelectedComponents(myEditor);
    if (selection.size() == 1 && selection.get(0) instanceof RadContainer) {
      RadContainer container = (RadContainer) selection.get(0);
      if (container.getLayoutManager().isGrid() && (container.getParent() instanceof RadRootContainer || container.getComponentCount() > 0)) {
        return container;
      }
    }
    RadContainer container = FormEditingUtil.getSelectionParent(selection);
    if (container == null && myEditor.getRootContainer().getComponentCount() > 0) {
      final RadComponent topComponent = myEditor.getRootContainer().getComponent(0);
      if (topComponent instanceof RadContainer) {
        container = (RadContainer) topComponent;
      }
    }
    if (container != null && !container.getLayoutManager().isGrid()) {
      return null;
    }
    return container;
  }

  public void selectedComponentChanged(GuiEditor source) {
    RadContainer container = getSelectedGridContainer();
    if (container != mySelectedContainer) {
      mySelectedContainer = container;
      mySelectionModel.clearSelection();
    }
    repaint();
  }

  @Nullable public Object getData(String dataId) {
    if (dataId.equals(GuiEditor.class.getName())) {
      return myEditor;
    }
    if (dataId.equals(CaptionSelection.class.getName())) {
      return new CaptionSelection(mySelectedContainer, myIsRow, getSelectedCells(null), mySelectionModel.getLeadSelectionIndex());
    }
    if (dataId.equals(DataConstantsEx.DELETE_ELEMENT_PROVIDER)) {
      return myDeleteProvider;
    }
    return myEditor.getData(dataId);
  }

  public void attachToScrollPane(final JScrollPane scrollPane) {
    scrollPane.getViewport().addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        repaint();
      }
    });
  }

  private int getCellAt(Point pnt) {
    if (mySelectedContainer == null) return -1;
    pnt = SwingUtilities.convertPoint(this, pnt, mySelectedContainer.getDelegee());
    return myIsRow ? mySelectedContainer.getGridRowAt(pnt.y) : mySelectedContainer.getGridColumnAt(pnt.x);
  }

  public int[] getSelectedCells(@Nullable final Point dragOrigin) {
    ArrayList<Integer> selection = new ArrayList<Integer>();
    RadContainer container = getSelectedGridContainer();
    if (container == null) {
      return new int[0];
    }
    int size = getCellCount();
    for(int i=0; i<size; i++) {
      if (mySelectionModel.isSelectedIndex(i)) {
        selection.add(i);
      }
    }
    if (selection.size() == 0 && dragOrigin != null) {
      int cell = getCellAt(dragOrigin);
      if (cell >= 0) {
        return new int[] { cell };
      }
    }
    int[] result = new int[selection.size()];
    for(int i=0; i<selection.size(); i++) {
      result [i] = selection.get(i).intValue();
    }
    return result;
  }

  private int getCellCount() {
    final RadContainer gridContainer = getSelectedGridContainer();
    assert gridContainer != null;
    return myIsRow ? gridContainer.getGridRowCount() : gridContainer.getGridColumnCount();
  }

  private class MyMouseListener extends MouseAdapter implements MouseMotionListener {
    private static final int MINIMUM_RESIZED_SIZE = 8;

    @Override public void mouseExited(MouseEvent e) {
      setCursor(Cursor.getDefaultCursor());
    }

    @Override public void mousePressed(MouseEvent e) {
      if (mySelectedContainer == null) return;
      requestFocus();
      Point pnt = SwingUtilities.convertPoint(GridCaptionPanel.this, e.getPoint(),
                                              mySelectedContainer.getDelegee());
      RadAbstractGridLayoutManager layout = mySelectedContainer.getGridLayoutManager();
      if (layout.canResizeCells()) {
        myResizeLine = layout.getGridLineNear(mySelectedContainer, myIsRow, pnt, 4);
      }
      if (!checkShowPopupMenu(e)) {
        int cell = getCellAt(e.getPoint());
        if (cell == -1) return;
        if ((e.getModifiers() & MouseEvent.CTRL_MASK) != 0) {
          mySelectionModel.addSelectionInterval(cell, cell);
        }
        else if ((e.getModifiers() & MouseEvent.SHIFT_MASK) != 0) {
          mySelectionModel.addSelectionInterval(mySelectionModel.getAnchorSelectionIndex(), cell);
        }
      }
    }

    @Override public void mouseReleased(MouseEvent e) {
      setCursor(Cursor.getDefaultCursor());
      myEditor.getActiveDecorationLayer().removeFeedback();

      if (myResizeLine > 0) {
        Point pnt = SwingUtilities.convertPoint(GridCaptionPanel.this, e.getPoint(),
                                                mySelectedContainer.getDelegee());
        doResize(pnt);
        myResizeLine = -1;
      }

      if (!checkShowPopupMenu(e)) {
        int cell = getCellAt(e.getPoint());
        if (cell == -1) return;
        if ((e.getModifiers() & (MouseEvent.CTRL_MASK | MouseEvent.SHIFT_MASK)) == 0) {
          mySelectionModel.setSelectionInterval(cell, cell);
        }
      }
    }

    private boolean checkShowPopupMenu(final MouseEvent e) {
      int cell = getCellAt(e.getPoint());

      if (cell >= 0 && e.isPopupTrigger()) {
        ActionGroup group = mySelectedContainer.getGridLayoutManager().getCaptionActions();
        if (group != null) {
          final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, group);
          popupMenu.getComponent().show(GridCaptionPanel.this, e.getX(), e.getY());
          return true;
        }
      }
      return false;
    }

    private void doResize(final Point pnt) {
      int[] coords = mySelectedContainer.getGridLayoutManager().getGridCellCoords(mySelectedContainer, myIsRow);
      int prevCoord = coords [myResizeLine-1];
      int newCoord = myIsRow ? pnt.y : pnt.x;
      if (newCoord < prevCoord + MINIMUM_RESIZED_SIZE) {
        return;
      }
      int newSize = newCoord - prevCoord;

      if (!myEditor.ensureEditable()) {
        return;
      }

      mySelectedContainer.getGridLayoutManager().processCellResized(mySelectedContainer, myIsRow, myResizeLine-1, newSize);
      mySelectedContainer.revalidate();
      myEditor.refreshAndSave(true);
    }

    public void mouseMoved(MouseEvent e) {
      if (mySelectedContainer == null || !mySelectedContainer.getGridLayoutManager().canResizeCells()) return;
      Point pnt = SwingUtilities.convertPoint(GridCaptionPanel.this, e.getPoint(),
                                              mySelectedContainer.getDelegee());
      int gridLine = mySelectedContainer.getGridLayoutManager().getGridLineNear(mySelectedContainer, myIsRow, pnt, 4);

      // first grid line may not be dragged
      if (gridLine <= 0) {
        setCursor(Cursor.getDefaultCursor());
      }
      else if (myIsRow) {
        setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
      }
      else {
        setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
      }
    }

    public void mouseDragged(MouseEvent e) {
      if (myResizeLine > 0) {
        Point pnt = SwingUtilities.convertPoint(GridCaptionPanel.this, e.getPoint(),
                                                mySelectedContainer.getDelegee());
        int[] coords = mySelectedContainer.getGridLayoutManager().getGridCellCoords(mySelectedContainer, myIsRow);
        int prevCoord = coords [myResizeLine-1];
        int newCoord = myIsRow ? pnt.y : pnt.x;
        if (newCoord < prevCoord + MINIMUM_RESIZED_SIZE) {
          return;
        }
        int newSize = newCoord - prevCoord;

        String toolTip = mySelectedContainer.getGridLayoutManager().getCellResizeTooltip(mySelectedContainer, myIsRow, myResizeLine-1, newSize);
        final ActiveDecorationLayer layer = myEditor.getActiveDecorationLayer();
        Rectangle rc;
        if (myIsRow) {
          rc = new Rectangle(0, e.getPoint().y, layer.getSize().width, 1);
        }
        else {
          rc = new Rectangle(e.getPoint().x, 0, 1, layer.getSize().height);
        }
        layer.putFeedback(GridCaptionPanel.this, rc, myFeedbackPainter, toolTip);
      }
    }
  }

  private static class LineFeedbackPainter implements FeedbackPainter {
    public void paintFeedback(Graphics2D g, Rectangle rc) {
      g.setColor(LightColors.YELLOW);
      if (rc.width == 1) {
        g.drawLine(rc.x, rc.y, rc.x, rc.y+rc.height);
      }
      else {
        g.drawLine(rc.x, rc.y, rc.x+rc.width, rc.y);
      }
    }
  }

  private class MyDeleteProvider implements DeleteProvider {
    public void deleteElement(DataContext dataContext) {
      int selectedIndex = mySelectionModel.getMinSelectionIndex();
      if (selectedIndex >= 0) {
        FormEditingUtil.deleteRowOrColumn(myEditor, mySelectedContainer, selectedIndex, myIsRow);
      }
    }

    public boolean canDeleteElement(DataContext dataContext) {
      if (mySelectedContainer == null || mySelectionModel.isSelectionEmpty()) {
        return false;
      }
      if (myIsRow) {
        return mySelectedContainer.getGridRowCount() > 1;
      }
      return mySelectedContainer.getGridColumnCount() > 1;
    }
  }

  private class MyDnDSource implements DnDSource {
    public boolean canStartDragging(DnDAction action, Point dragOrigin) {
      LOG.debug("canStartDragging(): dragOrigin=" + dragOrigin);
      if (myResizeLine != -1) {
        LOG.debug("canStartDragging(): have resize line");
        return false;
      }
      RadContainer container = getSelectedGridContainer();
      if (container != null &&
          container.getGridLayoutManager().getGridLineNear(mySelectedContainer, myIsRow, dragOrigin, 4) != -1) {
        LOG.debug("canStartDragging(): have gridline near");
        return false;
      }
      int[] selectedCells = getSelectedCells(dragOrigin);
      for(int cell: selectedCells) {
        if (!canDragCell(cell)) {
          LOG.debug("canStartDragging(): cannot drag cell");
          return false;
        }
      }
      LOG.debug("canStartDragging(): starting drag");
      return true;
    }

    private boolean canDragCell(final int cell) {
      if (mySelectedContainer == null) return false;
      for(RadComponent c: mySelectedContainer.getComponents()) {
        if (c.getConstraints().contains(myIsRow, cell) && c.getConstraints().getSpan(myIsRow) > 1) {
          return false;
        }
      }
      return true;
    }

    public DnDDragStartBean startDragging(DnDAction action, Point dragOrigin) {
      return new DnDDragStartBean(new MyDragBean(myIsRow, getSelectedCells(dragOrigin)));
    }

    @Nullable
    public Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin) {
      return null;
    }

    public void dragDropEnd() {
    }

    public void dropActionChanged(final int gestureModifiers) {
    }
  }

  private class MyDnDTarget implements DnDTarget {
    public boolean update(DnDEvent aEvent) {
      aEvent.setDropPossible(false, null);
      if (mySelectedContainer == null) {
        return false;
      }
      if (!(aEvent.getAttachedObject() instanceof MyDragBean)) {
        return false;
      }
      MyDragBean bean = (MyDragBean) aEvent.getAttachedObject();
      if (bean.isRow != myIsRow || bean.cells.length == 0) {
        return false;
      }
      int gridLine = getDropGridLine(aEvent);
      setDropInsertLine(gridLine);
      aEvent.setDropPossible(gridLine >= 0, null);
      if (gridLine >= 0) {
        FeedbackPainter painter = myIsRow ? HorzInsertFeedbackPainter.INSTANCE : VertInsertFeedbackPainter.INSTANCE;
        Rectangle rcFeedback = new Rectangle(mySelectedContainer.getDelegee().getSize());
        Rectangle cellRect = new Rectangle(gridLine, gridLine, 1, 1);
        rcFeedback = GridInsertLocation.getInsertFeedbackPosition(myIsRow ? GridInsertMode.RowBefore : GridInsertMode.ColumnBefore,
                                                                  mySelectedContainer, cellRect, rcFeedback);
        myEditor.getActiveDecorationLayer().putFeedback(mySelectedContainer.getDelegee(), rcFeedback, painter, null);
      }
      else {
        myEditor.getActiveDecorationLayer().removeFeedback();
      }
      return false;
    }

    private int getDropGridLine(final DnDEvent aEvent) {
      final Point point = aEvent.getPointOn(mySelectedContainer.getDelegee());
      return mySelectedContainer.getGridLayoutManager().getGridLineNear(mySelectedContainer, myIsRow, point, 20);
    }

    public void drop(DnDEvent aEvent) {
      if (!(aEvent.getAttachedObject() instanceof MyDragBean)) {
        return;
      }
      MyDragBean dragBean = (MyDragBean) aEvent.getAttachedObject();
      int targetCell = getDropGridLine(aEvent);
      if (targetCell < 0) return;
      mySelectedContainer.getGridLayoutManager().processCellsMoved(mySelectedContainer, myIsRow, dragBean.cells, targetCell);
      mySelectionModel.clearSelection();
      mySelectedContainer.revalidate();
      myEditor.refreshAndSave(true);
      cleanUpOnLeave();
    }

    public void cleanUpOnLeave() {
      setDropInsertLine(-1);
      myEditor.getActiveDecorationLayer().removeFeedback();
    }

    public void updateDraggedImage(Image image, Point dropPoint, Point imageOffset) {
    }

    private void setDropInsertLine(final int i) {
      if (myDropInsertLine != i) {
        myDropInsertLine = i;
        repaint();
      }
    }
  }

  private static class MyDragBean {
    public boolean isRow;
    public int[] cells;

    public MyDragBean(final boolean row, final int[] cells) {
      isRow = row;
      this.cells = cells;
    }
  }

  private class MyKeyListener extends KeyAdapter {
    @Override public void keyPressed(KeyEvent e) {
      int cellCount = getCellCount();
      int leadIndex = mySelectionModel.getLeadSelectionIndex();
      if (e.getKeyCode() == KeyEvent.VK_HOME) {
        mySelectionModel.setSelectionInterval(0, 0);
      }
      else if (e.getKeyCode() == KeyEvent.VK_END) {
        mySelectionModel.setSelectionInterval(cellCount-1, cellCount-1);
      }
      else if (e.getKeyCode() == (myIsRow ? KeyEvent.VK_UP : KeyEvent.VK_LEFT)) {
        if (leadIndex > 0) {
          mySelectionModel.setSelectionInterval(leadIndex-1, leadIndex-1);
        }
      }
      else if (e.getKeyCode() == (myIsRow ? KeyEvent.VK_DOWN : KeyEvent.VK_RIGHT)) {
        if (leadIndex < cellCount-1) {
          mySelectionModel.setSelectionInterval(leadIndex+1, leadIndex+1);
        }
      }
    }
  }
}
