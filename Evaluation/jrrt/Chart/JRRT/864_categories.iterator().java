package org.jfree.chart.axis;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jfree.chart.entity.CategoryLabelEntity;
import org.jfree.chart.entity.EntityCollection;
import org.jfree.chart.event.AxisChangeEvent;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.text.G2TextMeasurer;
import org.jfree.chart.text.TextBlock;
import org.jfree.chart.text.TextUtilities;
import org.jfree.chart.util.ObjectUtilities;
import org.jfree.chart.util.PaintUtilities;
import org.jfree.chart.util.RectangleAnchor;
import org.jfree.chart.util.RectangleEdge;
import org.jfree.chart.util.RectangleInsets;
import org.jfree.chart.util.SerialUtilities;
import org.jfree.chart.util.ShapeUtilities;
import org.jfree.chart.util.Size2D;
import org.jfree.data.category.CategoryDataset;

public class CategoryAxis extends Axis implements Cloneable, Serializable  {
  final private static long serialVersionUID = 5886554608114265863L;
  final public static double DEFAULT_AXIS_MARGIN = 0.05D;
  final public static double DEFAULT_CATEGORY_MARGIN = 0.20D;
  private double lowerMargin;
  private double upperMargin;
  private double categoryMargin;
  private int maximumCategoryLabelLines;
  private float maximumCategoryLabelWidthRatio;
  private int categoryLabelPositionOffset;
  private CategoryLabelPositions categoryLabelPositions;
  private Map tickLabelFontMap;
  private transient Map tickLabelPaintMap;
  private Map categoryLabelToolTips;
  public CategoryAxis() {
    this(null);
  }
  public CategoryAxis(String label) {
    super(label);
    this.lowerMargin = DEFAULT_AXIS_MARGIN;
    this.upperMargin = DEFAULT_AXIS_MARGIN;
    this.categoryMargin = DEFAULT_CATEGORY_MARGIN;
    this.maximumCategoryLabelLines = 1;
    this.maximumCategoryLabelWidthRatio = 0.0F;
    this.categoryLabelPositionOffset = 4;
    this.categoryLabelPositions = CategoryLabelPositions.STANDARD;
    this.tickLabelFontMap = new HashMap();
    this.tickLabelPaintMap = new HashMap();
    this.categoryLabelToolTips = new HashMap();
  }
  public AxisSpace reserveSpace(Graphics2D g2, Plot plot, Rectangle2D plotArea, RectangleEdge edge, AxisSpace space) {
    if(space == null) {
      space = new AxisSpace();
    }
    if(!isVisible()) {
      return space;
    }
    double tickLabelHeight = 0.0D;
    double tickLabelWidth = 0.0D;
    if(isTickLabelsVisible()) {
      g2.setFont(getTickLabelFont());
      AxisState state = new AxisState();
      refreshTicks(g2, state, plotArea, edge);
      if(edge == RectangleEdge.TOP) {
        tickLabelHeight = state.getMax();
      }
      else 
        if(edge == RectangleEdge.BOTTOM) {
          tickLabelHeight = state.getMax();
        }
        else 
          if(edge == RectangleEdge.LEFT) {
            tickLabelWidth = state.getMax();
          }
          else 
            if(edge == RectangleEdge.RIGHT) {
              tickLabelWidth = state.getMax();
            }
    }
    Rectangle2D labelEnclosure = getLabelEnclosure(g2, edge);
    double labelHeight = 0.0D;
    double labelWidth = 0.0D;
    if(RectangleEdge.isTopOrBottom(edge)) {
      labelHeight = labelEnclosure.getHeight();
      space.add(labelHeight + tickLabelHeight + this.categoryLabelPositionOffset, edge);
    }
    else 
      if(RectangleEdge.isLeftOrRight(edge)) {
        labelWidth = labelEnclosure.getWidth();
        space.add(labelWidth + tickLabelWidth + this.categoryLabelPositionOffset, edge);
      }
    return space;
  }
  public AxisState draw(Graphics2D g2, double cursor, Rectangle2D plotArea, Rectangle2D dataArea, RectangleEdge edge, PlotRenderingInfo plotState) {
    if(!isVisible()) {
      return new AxisState(cursor);
    }
    if(isAxisLineVisible()) {
      drawAxisLine(g2, cursor, dataArea, edge);
    }
    AxisState state = new AxisState(cursor);
    if(isTickMarksVisible()) {
      drawTickMarks(g2, cursor, dataArea, edge, state);
    }
    createAndAddEntity(cursor, state, dataArea, edge, plotState);
    state = drawCategoryLabels(g2, plotArea, dataArea, edge, state, plotState);
    state = drawLabel(getLabel(), g2, plotArea, dataArea, edge, state, plotState);
    return state;
  }
  protected AxisState drawCategoryLabels(Graphics2D g2, Rectangle2D plotArea, Rectangle2D dataArea, RectangleEdge edge, AxisState state, PlotRenderingInfo plotState) {
    if(state == null) {
      throw new IllegalArgumentException("Null \'state\' argument.");
    }
    if(isTickLabelsVisible()) {
      List ticks = refreshTicks(g2, state, plotArea, edge);
      state.setTicks(ticks);
      int categoryIndex = 0;
      Iterator iterator = ticks.iterator();
      while(iterator.hasNext()){
        CategoryTick tick = (CategoryTick)iterator.next();
        g2.setFont(getTickLabelFont(tick.getCategory()));
        g2.setPaint(getTickLabelPaint(tick.getCategory()));
        CategoryLabelPosition position = this.categoryLabelPositions.getLabelPosition(edge);
        double x0 = 0.0D;
        double x1 = 0.0D;
        double y0 = 0.0D;
        double y1 = 0.0D;
        if(edge == RectangleEdge.TOP) {
          x0 = getCategoryStart(categoryIndex, ticks.size(), dataArea, edge);
          x1 = getCategoryEnd(categoryIndex, ticks.size(), dataArea, edge);
          y1 = state.getCursor() - this.categoryLabelPositionOffset;
          y0 = y1 - state.getMax();
        }
        else 
          if(edge == RectangleEdge.BOTTOM) {
            x0 = getCategoryStart(categoryIndex, ticks.size(), dataArea, edge);
            x1 = getCategoryEnd(categoryIndex, ticks.size(), dataArea, edge);
            y0 = state.getCursor() + this.categoryLabelPositionOffset;
            y1 = y0 + state.getMax();
          }
          else 
            if(edge == RectangleEdge.LEFT) {
              y0 = getCategoryStart(categoryIndex, ticks.size(), dataArea, edge);
              y1 = getCategoryEnd(categoryIndex, ticks.size(), dataArea, edge);
              x1 = state.getCursor() - this.categoryLabelPositionOffset;
              x0 = x1 - state.getMax();
            }
            else 
              if(edge == RectangleEdge.RIGHT) {
                y0 = getCategoryStart(categoryIndex, ticks.size(), dataArea, edge);
                y1 = getCategoryEnd(categoryIndex, ticks.size(), dataArea, edge);
                x0 = state.getCursor() + this.categoryLabelPositionOffset;
                x1 = x0 - state.getMax();
              }
        Rectangle2D area = new Rectangle2D.Double(x0, y0, (x1 - x0), (y1 - y0));
        Point2D anchorPoint = RectangleAnchor.coordinates(area, position.getCategoryAnchor());
        TextBlock block = tick.getLabel();
        block.draw(g2, (float)anchorPoint.getX(), (float)anchorPoint.getY(), position.getLabelAnchor(), (float)anchorPoint.getX(), (float)anchorPoint.getY(), position.getAngle());
        Shape bounds = block.calculateBounds(g2, (float)anchorPoint.getX(), (float)anchorPoint.getY(), position.getLabelAnchor(), (float)anchorPoint.getX(), (float)anchorPoint.getY(), position.getAngle());
        if(plotState != null && plotState.getOwner() != null) {
          EntityCollection entities = plotState.getOwner().getEntityCollection();
          if(entities != null) {
            String tooltip = getCategoryLabelToolTip(tick.getCategory());
            entities.add(new CategoryLabelEntity(tick.getCategory(), bounds, tooltip, null));
          }
        }
        categoryIndex++;
      }
      if(edge.equals(RectangleEdge.TOP)) {
        double h = state.getMax() + this.categoryLabelPositionOffset;
        state.cursorUp(h);
      }
      else 
        if(edge.equals(RectangleEdge.BOTTOM)) {
          double h = state.getMax() + this.categoryLabelPositionOffset;
          state.cursorDown(h);
        }
        else 
          if(edge == RectangleEdge.LEFT) {
            double w = state.getMax() + this.categoryLabelPositionOffset;
            state.cursorLeft(w);
          }
          else 
            if(edge == RectangleEdge.RIGHT) {
              double w = state.getMax() + this.categoryLabelPositionOffset;
              state.cursorRight(w);
            }
    }
    return state;
  }
  public CategoryLabelPositions getCategoryLabelPositions() {
    return this.categoryLabelPositions;
  }
  public Font getTickLabelFont(Comparable category) {
    if(category == null) {
      throw new IllegalArgumentException("Null \'category\' argument.");
    }
    Font result = (Font)this.tickLabelFontMap.get(category);
    if(result == null) {
      result = getTickLabelFont();
    }
    return result;
  }
  public List refreshTicks(Graphics2D g2, AxisState state, Rectangle2D dataArea, RectangleEdge edge) {
    List ticks = new java.util.ArrayList();
    if(dataArea.getHeight() <= 0.0D || dataArea.getWidth() < 0.0D) {
      return ticks;
    }
    CategoryPlot plot = (CategoryPlot)getPlot();
    List categories = plot.getCategoriesForAxis(this);
    double max = 0.0D;
    if(categories != null) {
      CategoryLabelPosition position = this.categoryLabelPositions.getLabelPosition(edge);
      float r = this.maximumCategoryLabelWidthRatio;
      if(r <= 0.0D) {
        r = position.getWidthRatio();
      }
      float l = 0.0F;
      if(position.getWidthType() == CategoryLabelWidthType.CATEGORY) {
        l = (float)calculateCategorySize(categories.size(), dataArea, edge);
      }
      else {
        if(RectangleEdge.isLeftOrRight(edge)) {
          l = (float)dataArea.getWidth();
        }
        else {
          l = (float)dataArea.getHeight();
        }
      }
      int categoryIndex = 0;
      Iterator iterator = categories.iterator();
      while(iterator.hasNext()){
        Comparable category = (Comparable)iterator.next();
        g2.setFont(getTickLabelFont(category));
        TextBlock label = createLabel(category, l * r, edge, g2);
        if(edge == RectangleEdge.TOP || edge == RectangleEdge.BOTTOM) {
          max = Math.max(max, calculateTextBlockHeight(label, position, g2));
        }
        else 
          if(edge == RectangleEdge.LEFT || edge == RectangleEdge.RIGHT) {
            max = Math.max(max, calculateTextBlockWidth(label, position, g2));
          }
        Tick tick = new CategoryTick(category, label, position.getLabelAnchor(), position.getRotationAnchor(), position.getAngle());
        ticks.add(tick);
        categoryIndex = categoryIndex + 1;
      }
    }
    state.setMax(max);
    return ticks;
  }
  private Map readPaintMap(ObjectInputStream in) throws IOException, ClassNotFoundException {
    boolean isNull = in.readBoolean();
    if(isNull) {
      return null;
    }
    Map result = new HashMap();
    int count = in.readInt();
    for(int i = 0; i < count; i++) {
      Comparable category = (Comparable)in.readObject();
      Paint paint = SerialUtilities.readPaint(in);
      result.put(category, paint);
    }
    return result;
  }
  public Object clone() throws CloneNotSupportedException {
    CategoryAxis clone = (CategoryAxis)super.clone();
    clone.tickLabelFontMap = new HashMap(this.tickLabelFontMap);
    clone.tickLabelPaintMap = new HashMap(this.tickLabelPaintMap);
    clone.categoryLabelToolTips = new HashMap(this.categoryLabelToolTips);
    return clone;
  }
  public Paint getTickLabelPaint(Comparable category) {
    if(category == null) {
      throw new IllegalArgumentException("Null \'category\' argument.");
    }
    Paint result = (Paint)this.tickLabelPaintMap.get(category);
    if(result == null) {
      result = getTickLabelPaint();
    }
    return result;
  }
  public String getCategoryLabelToolTip(Comparable category) {
    if(category == null) {
      throw new IllegalArgumentException("Null \'category\' argument.");
    }
    return (String)this.categoryLabelToolTips.get(category);
  }
  protected TextBlock createLabel(Comparable category, float width, RectangleEdge edge, Graphics2D g2) {
    TextBlock label = TextUtilities.createTextBlock(category.toString(), getTickLabelFont(category), getTickLabelPaint(category), width, this.maximumCategoryLabelLines, new G2TextMeasurer(g2));
    return label;
  }
  private boolean equalPaintMaps(Map map1, Map map2) {
    if(map1.size() != map2.size()) {
      return false;
    }
    Set entries = map1.entrySet();
    Iterator iterator = entries.iterator();
    while(iterator.hasNext()){
      Map.Entry entry = (Map.Entry)iterator.next();
      Paint p1 = (Paint)entry.getValue();
      Paint p2 = (Paint)map2.get(entry.getKey());
      if(!PaintUtilities.equal(p1, p2)) {
        return false;
      }
    }
    return true;
  }
  public boolean equals(Object obj) {
    if(obj == this) {
      return true;
    }
    if(!(obj instanceof CategoryAxis)) {
      return false;
    }
    if(!super.equals(obj)) {
      return false;
    }
    CategoryAxis that = (CategoryAxis)obj;
    if(that.lowerMargin != this.lowerMargin) {
      return false;
    }
    if(that.upperMargin != this.upperMargin) {
      return false;
    }
    if(that.categoryMargin != this.categoryMargin) {
      return false;
    }
    if(that.maximumCategoryLabelWidthRatio != this.maximumCategoryLabelWidthRatio) {
      return false;
    }
    if(that.categoryLabelPositionOffset != this.categoryLabelPositionOffset) {
      return false;
    }
    if(!ObjectUtilities.equal(that.categoryLabelPositions, this.categoryLabelPositions)) {
      return false;
    }
    if(!ObjectUtilities.equal(that.categoryLabelToolTips, this.categoryLabelToolTips)) {
      return false;
    }
    if(!ObjectUtilities.equal(this.tickLabelFontMap, that.tickLabelFontMap)) {
      return false;
    }
    if(!equalPaintMaps(this.tickLabelPaintMap, that.tickLabelPaintMap)) {
      return false;
    }
    return true;
  }
  protected double calculateCategoryGapSize(int categoryCount, Rectangle2D area, RectangleEdge edge) {
    double result = 0.0D;
    double available = 0.0D;
    if((edge == RectangleEdge.TOP) || (edge == RectangleEdge.BOTTOM)) {
      available = area.getWidth();
    }
    else 
      if((edge == RectangleEdge.LEFT) || (edge == RectangleEdge.RIGHT)) {
        available = area.getHeight();
      }
    if(categoryCount > 1) {
      result = available * getCategoryMargin() / (categoryCount - 1);
    }
    return result;
  }
  protected double calculateCategorySize(int categoryCount, Rectangle2D area, RectangleEdge edge) {
    double result = 0.0D;
    double available = 0.0D;
    if((edge == RectangleEdge.TOP) || (edge == RectangleEdge.BOTTOM)) {
      available = area.getWidth();
    }
    else 
      if((edge == RectangleEdge.LEFT) || (edge == RectangleEdge.RIGHT)) {
        available = area.getHeight();
      }
    if(categoryCount > 1) {
      result = available * (1 - getLowerMargin() - getUpperMargin() - getCategoryMargin());
      result = result / categoryCount;
    }
    else {
      result = available * (1 - getLowerMargin() - getUpperMargin());
    }
    return result;
  }
  protected double calculateTextBlockHeight(TextBlock block, CategoryLabelPosition position, Graphics2D g2) {
    RectangleInsets insets = getTickLabelInsets();
    Size2D size = block.calculateDimensions(g2);
    Rectangle2D box = new Rectangle2D.Double(0.0D, 0.0D, size.getWidth(), size.getHeight());
    Shape rotatedBox = ShapeUtilities.rotateShape(box, position.getAngle(), 0.0F, 0.0F);
    double h = rotatedBox.getBounds2D().getHeight() + insets.getTop() + insets.getBottom();
    return h;
  }
  protected double calculateTextBlockWidth(TextBlock block, CategoryLabelPosition position, Graphics2D g2) {
    RectangleInsets insets = getTickLabelInsets();
    Size2D size = block.calculateDimensions(g2);
    Rectangle2D box = new Rectangle2D.Double(0.0D, 0.0D, size.getWidth(), size.getHeight());
    Shape rotatedBox = ShapeUtilities.rotateShape(box, position.getAngle(), 0.0F, 0.0F);
    double w = rotatedBox.getBounds2D().getWidth() + insets.getLeft() + insets.getRight();
    return w;
  }
  public double getCategoryEnd(int category, int categoryCount, Rectangle2D area, RectangleEdge edge) {
    return getCategoryStart(category, categoryCount, area, edge) + calculateCategorySize(categoryCount, area, edge);
  }
  public double getCategoryJava2DCoordinate(CategoryAnchor anchor, int category, int categoryCount, Rectangle2D area, RectangleEdge edge) {
    double result = 0.0D;
    if(anchor == CategoryAnchor.START) {
      result = getCategoryStart(category, categoryCount, area, edge);
    }
    else 
      if(anchor == CategoryAnchor.MIDDLE) {
        result = getCategoryMiddle(category, categoryCount, area, edge);
      }
      else 
        if(anchor == CategoryAnchor.END) {
          result = getCategoryEnd(category, categoryCount, area, edge);
        }
    return result;
  }
  public double getCategoryMargin() {
    return this.categoryMargin;
  }
  public double getCategoryMiddle(int category, int categoryCount, Rectangle2D area, RectangleEdge edge) {
    if(category < 0 || category >= categoryCount) {
      throw new IllegalArgumentException("Invalid category index: " + category);
    }
    return getCategoryStart(category, categoryCount, area, edge) + calculateCategorySize(categoryCount, area, edge) / 2;
  }
  public double getCategoryMiddle(Comparable category, List categories, Rectangle2D area, RectangleEdge edge) {
    if(categories == null) {
      throw new IllegalArgumentException("Null \'categories\' argument.");
    }
    int categoryIndex = categories.indexOf(category);
    int categoryCount = categories.size();
    return getCategoryMiddle(categoryIndex, categoryCount, area, edge);
  }
  public double getCategorySeriesMiddle(int categoryIndex, int categoryCount, int seriesIndex, int seriesCount, double itemMargin, Rectangle2D area, RectangleEdge edge) {
    double start = getCategoryStart(categoryIndex, categoryCount, area, edge);
    double end = getCategoryEnd(categoryIndex, categoryCount, area, edge);
    double width = end - start;
    if(seriesCount == 1) {
      return start + width / 2.0D;
    }
    else {
      double gap = (width * itemMargin) / (seriesCount - 1);
      double ww = (width * (1 - itemMargin)) / seriesCount;
      return start + (seriesIndex * (ww + gap)) + ww / 2.0D;
    }
  }
  public double getCategorySeriesMiddle(Comparable category, Comparable seriesKey, CategoryDataset dataset, double itemMargin, Rectangle2D area, RectangleEdge edge) {
    int categoryIndex = dataset.getColumnIndex(category);
    int categoryCount = dataset.getColumnCount();
    int seriesIndex = dataset.getRowIndex(seriesKey);
    int seriesCount = dataset.getRowCount();
    double start = getCategoryStart(categoryIndex, categoryCount, area, edge);
    double end = getCategoryEnd(categoryIndex, categoryCount, area, edge);
    double width = end - start;
    if(seriesCount == 1) {
      return start + width / 2.0D;
    }
    else {
      double gap = (width * itemMargin) / (seriesCount - 1);
      double ww = (width * (1 - itemMargin)) / seriesCount;
      return start + (seriesIndex * (ww + gap)) + ww / 2.0D;
    }
  }
  public double getCategoryStart(int category, int categoryCount, Rectangle2D area, RectangleEdge edge) {
    double result = 0.0D;
    if((edge == RectangleEdge.TOP) || (edge == RectangleEdge.BOTTOM)) {
      result = area.getX() + area.getWidth() * getLowerMargin();
    }
    else 
      if((edge == RectangleEdge.LEFT) || (edge == RectangleEdge.RIGHT)) {
        result = area.getMinY() + area.getHeight() * getLowerMargin();
      }
    double categorySize = calculateCategorySize(categoryCount, area, edge);
    double categoryGapWidth = calculateCategoryGapSize(categoryCount, area, edge);
    result = result + category * (categorySize + categoryGapWidth);
    return result;
  }
  public double getLowerMargin() {
    return this.lowerMargin;
  }
  public double getUpperMargin() {
    return this.upperMargin;
  }
  public float getMaximumCategoryLabelWidthRatio() {
    return this.maximumCategoryLabelWidthRatio;
  }
  public int getCategoryLabelPositionOffset() {
    return this.categoryLabelPositionOffset;
  }
  public int getMaximumCategoryLabelLines() {
    return this.maximumCategoryLabelLines;
  }
  public int hashCode() {
    if(getLabel() != null) {
      return getLabel().hashCode();
    }
    else {
      return 0;
    }
  }
  public void addCategoryLabelToolTip(Comparable category, String tooltip) {
    if(category == null) {
      throw new IllegalArgumentException("Null \'category\' argument.");
    }
    this.categoryLabelToolTips.put(category, tooltip);
    notifyListeners(new AxisChangeEvent(this));
  }
  public void clearCategoryLabelToolTips() {
    this.categoryLabelToolTips.clear();
    notifyListeners(new AxisChangeEvent(this));
  }
  public void configure() {
  }
  public void drawTickMarks(Graphics2D g2, double cursor, Rectangle2D dataArea, RectangleEdge edge, AxisState state) {
    Plot p = getPlot();
    if(p == null) {
      return ;
    }
    CategoryPlot plot = (CategoryPlot)p;
    double il = getTickMarkInsideLength();
    double ol = getTickMarkOutsideLength();
    Line2D line = new Line2D.Double();
    List categories = plot.getCategoriesForAxis(this);
    g2.setPaint(getTickMarkPaint());
    g2.setStroke(getTickMarkStroke());
    if(edge.equals(RectangleEdge.TOP)) {
      Iterator iterator = categories.iterator();
      while(iterator.hasNext()){
        Comparable key = (Comparable)iterator.next();
        double x = getCategoryMiddle(key, categories, dataArea, edge);
        line.setLine(x, cursor, x, cursor + il);
        g2.draw(line);
        line.setLine(x, cursor, x, cursor - ol);
        g2.draw(line);
      }
      state.cursorUp(ol);
    }
    else 
      if(edge.equals(RectangleEdge.BOTTOM)) {
        Iterator var_864 = categories.iterator();
        Iterator iterator = var_864;
        while(iterator.hasNext()){
          Comparable key = (Comparable)iterator.next();
          double x = getCategoryMiddle(key, categories, dataArea, edge);
          line.setLine(x, cursor, x, cursor - il);
          g2.draw(line);
          line.setLine(x, cursor, x, cursor + ol);
          g2.draw(line);
        }
        state.cursorDown(ol);
      }
      else 
        if(edge.equals(RectangleEdge.LEFT)) {
          Iterator iterator = categories.iterator();
          while(iterator.hasNext()){
            Comparable key = (Comparable)iterator.next();
            double y = getCategoryMiddle(key, categories, dataArea, edge);
            line.setLine(cursor, y, cursor + il, y);
            g2.draw(line);
            line.setLine(cursor, y, cursor - ol, y);
            g2.draw(line);
          }
          state.cursorLeft(ol);
        }
        else 
          if(edge.equals(RectangleEdge.RIGHT)) {
            Iterator iterator = categories.iterator();
            while(iterator.hasNext()){
              Comparable key = (Comparable)iterator.next();
              double y = getCategoryMiddle(key, categories, dataArea, edge);
              line.setLine(cursor, y, cursor - il, y);
              g2.draw(line);
              line.setLine(cursor, y, cursor + ol, y);
              g2.draw(line);
            }
            state.cursorRight(ol);
          }
  }
  private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
    stream.defaultReadObject();
    this.tickLabelPaintMap = readPaintMap(stream);
  }
  public void removeCategoryLabelToolTip(Comparable category) {
    if(category == null) {
      throw new IllegalArgumentException("Null \'category\' argument.");
    }
    this.categoryLabelToolTips.remove(category);
    notifyListeners(new AxisChangeEvent(this));
  }
  public void setCategoryLabelPositionOffset(int offset) {
    this.categoryLabelPositionOffset = offset;
    notifyListeners(new AxisChangeEvent(this));
  }
  public void setCategoryLabelPositions(CategoryLabelPositions positions) {
    if(positions == null) {
      throw new IllegalArgumentException("Null \'positions\' argument.");
    }
    this.categoryLabelPositions = positions;
    notifyListeners(new AxisChangeEvent(this));
  }
  public void setCategoryMargin(double margin) {
    this.categoryMargin = margin;
    notifyListeners(new AxisChangeEvent(this));
  }
  public void setLowerMargin(double margin) {
    this.lowerMargin = margin;
    notifyListeners(new AxisChangeEvent(this));
  }
  public void setMaximumCategoryLabelLines(int lines) {
    this.maximumCategoryLabelLines = lines;
    notifyListeners(new AxisChangeEvent(this));
  }
  public void setMaximumCategoryLabelWidthRatio(float ratio) {
    this.maximumCategoryLabelWidthRatio = ratio;
    notifyListeners(new AxisChangeEvent(this));
  }
  public void setTickLabelFont(Comparable category, Font font) {
    if(category == null) {
      throw new IllegalArgumentException("Null \'category\' argument.");
    }
    if(font == null) {
      this.tickLabelFontMap.remove(category);
    }
    else {
      this.tickLabelFontMap.put(category, font);
    }
    notifyListeners(new AxisChangeEvent(this));
  }
  public void setTickLabelPaint(Comparable category, Paint paint) {
    if(category == null) {
      throw new IllegalArgumentException("Null \'category\' argument.");
    }
    if(paint == null) {
      this.tickLabelPaintMap.remove(category);
    }
    else {
      this.tickLabelPaintMap.put(category, paint);
    }
    notifyListeners(new AxisChangeEvent(this));
  }
  public void setUpperMargin(double margin) {
    this.upperMargin = margin;
    notifyListeners(new AxisChangeEvent(this));
  }
  private void writeObject(ObjectOutputStream stream) throws IOException {
    stream.defaultWriteObject();
    writePaintMap(this.tickLabelPaintMap, stream);
  }
  private void writePaintMap(Map map, ObjectOutputStream out) throws IOException {
    if(map == null) {
      out.writeBoolean(true);
    }
    else {
      out.writeBoolean(false);
      Set keys = map.keySet();
      int count = keys.size();
      out.writeInt(count);
      Iterator iterator = keys.iterator();
      while(iterator.hasNext()){
        Comparable key = (Comparable)iterator.next();
        out.writeObject(key);
        SerialUtilities.writePaint((Paint)map.get(key), out);
      }
    }
  }
}