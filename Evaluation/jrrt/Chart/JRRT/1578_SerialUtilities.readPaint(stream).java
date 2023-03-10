package org.jfree.chart.plot;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Paint;
import java.awt.Stroke;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.EventListener;
import javax.swing.event.EventListenerList;
import org.jfree.chart.event.MarkerChangeEvent;
import org.jfree.chart.event.MarkerChangeListener;
import org.jfree.chart.text.TextAnchor;
import org.jfree.chart.util.LengthAdjustmentType;
import org.jfree.chart.util.ObjectUtilities;
import org.jfree.chart.util.PaintUtilities;
import org.jfree.chart.util.RectangleAnchor;
import org.jfree.chart.util.RectangleInsets;
import org.jfree.chart.util.SerialUtilities;

abstract public class Marker implements Cloneable, Serializable  {
  final private static long serialVersionUID = -734389651405327166L;
  private transient Paint paint;
  private transient Stroke stroke;
  private transient Paint outlinePaint;
  private transient Stroke outlineStroke;
  private float alpha;
  private String label = null;
  private Font labelFont;
  private transient Paint labelPaint;
  private RectangleAnchor labelAnchor;
  private TextAnchor labelTextAnchor;
  private RectangleInsets labelOffset;
  private LengthAdjustmentType labelOffsetType;
  private transient EventListenerList listenerList;
  protected Marker() {
    this(Color.gray);
  }
  protected Marker(Paint paint) {
    this(paint, new BasicStroke(0.5F), Color.gray, new BasicStroke(0.5F), 0.80F);
  }
  protected Marker(Paint paint, Stroke stroke, Paint outlinePaint, Stroke outlineStroke, float alpha) {
    super();
    if(paint == null) {
      throw new IllegalArgumentException("Null \'paint\' argument.");
    }
    if(stroke == null) {
      throw new IllegalArgumentException("Null \'stroke\' argument.");
    }
    if(alpha < 0.0F || alpha > 1.0F) 
      throw new IllegalArgumentException("The \'alpha\' value must be in the range 0.0f to 1.0f");
    this.paint = paint;
    this.stroke = stroke;
    this.outlinePaint = outlinePaint;
    this.outlineStroke = outlineStroke;
    this.alpha = alpha;
    this.labelFont = new Font("Tahoma", Font.PLAIN, 9);
    this.labelPaint = Color.black;
    this.labelAnchor = RectangleAnchor.TOP_LEFT;
    this.labelOffset = new RectangleInsets(3.0D, 3.0D, 3.0D, 3.0D);
    this.labelOffsetType = LengthAdjustmentType.CONTRACT;
    this.labelTextAnchor = TextAnchor.CENTER;
    this.listenerList = new EventListenerList();
  }
  public EventListener[] getListeners(Class listenerType) {
    return this.listenerList.getListeners(listenerType);
  }
  public Font getLabelFont() {
    return this.labelFont;
  }
  public LengthAdjustmentType getLabelOffsetType() {
    return this.labelOffsetType;
  }
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
  public Paint getLabelPaint() {
    return this.labelPaint;
  }
  public Paint getOutlinePaint() {
    return this.outlinePaint;
  }
  public Paint getPaint() {
    return this.paint;
  }
  public RectangleAnchor getLabelAnchor() {
    return this.labelAnchor;
  }
  public RectangleInsets getLabelOffset() {
    return this.labelOffset;
  }
  public String getLabel() {
    return this.label;
  }
  public Stroke getOutlineStroke() {
    return this.outlineStroke;
  }
  public Stroke getStroke() {
    return this.stroke;
  }
  public TextAnchor getLabelTextAnchor() {
    return this.labelTextAnchor;
  }
  public boolean equals(Object obj) {
    if(obj == this) {
      return true;
    }
    if(!(obj instanceof Marker)) {
      return false;
    }
    Marker that = (Marker)obj;
    if(!PaintUtilities.equal(this.paint, that.paint)) {
      return false;
    }
    if(!ObjectUtilities.equal(this.stroke, that.stroke)) {
      return false;
    }
    if(!PaintUtilities.equal(this.outlinePaint, that.outlinePaint)) {
      return false;
    }
    if(!ObjectUtilities.equal(this.outlineStroke, that.outlineStroke)) {
      return false;
    }
    if(this.alpha != that.alpha) {
      return false;
    }
    if(!ObjectUtilities.equal(this.label, that.label)) {
      return false;
    }
    if(!ObjectUtilities.equal(this.labelFont, that.labelFont)) {
      return false;
    }
    if(!PaintUtilities.equal(this.labelPaint, that.labelPaint)) {
      return false;
    }
    if(this.labelAnchor != that.labelAnchor) {
      return false;
    }
    if(this.labelTextAnchor != that.labelTextAnchor) {
      return false;
    }
    if(!ObjectUtilities.equal(this.labelOffset, that.labelOffset)) {
      return false;
    }
    if(!this.labelOffsetType.equals(that.labelOffsetType)) {
      return false;
    }
    return true;
  }
  public float getAlpha() {
    return this.alpha;
  }
  public void addChangeListener(MarkerChangeListener listener) {
    this.listenerList.add(MarkerChangeListener.class, listener);
  }
  public void notifyListeners(MarkerChangeEvent event) {
    Object[] listeners = this.listenerList.getListenerList();
    for(int i = listeners.length - 2; i >= 0; i -= 2) {
      if(listeners[i] == MarkerChangeListener.class) {
        ((MarkerChangeListener)listeners[i + 1]).markerChanged(event);
      }
    }
  }
  private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
    stream.defaultReadObject();
    Paint var_1578 = SerialUtilities.readPaint(stream);
    this.paint = var_1578;
    this.stroke = SerialUtilities.readStroke(stream);
    this.outlinePaint = SerialUtilities.readPaint(stream);
    this.outlineStroke = SerialUtilities.readStroke(stream);
    this.labelPaint = SerialUtilities.readPaint(stream);
    this.listenerList = new EventListenerList();
  }
  public void removeChangeListener(MarkerChangeListener listener) {
    this.listenerList.remove(MarkerChangeListener.class, listener);
  }
  public void setAlpha(float alpha) {
    if(alpha < 0.0F || alpha > 1.0F) 
      throw new IllegalArgumentException("The \'alpha\' value must be in the range 0.0f to 1.0f");
    this.alpha = alpha;
    notifyListeners(new MarkerChangeEvent(this));
  }
  public void setLabel(String label) {
    this.label = label;
    notifyListeners(new MarkerChangeEvent(this));
  }
  public void setLabelAnchor(RectangleAnchor anchor) {
    if(anchor == null) {
      throw new IllegalArgumentException("Null \'anchor\' argument.");
    }
    this.labelAnchor = anchor;
    notifyListeners(new MarkerChangeEvent(this));
  }
  public void setLabelFont(Font font) {
    if(font == null) {
      throw new IllegalArgumentException("Null \'font\' argument.");
    }
    this.labelFont = font;
    notifyListeners(new MarkerChangeEvent(this));
  }
  public void setLabelOffset(RectangleInsets offset) {
    if(offset == null) {
      throw new IllegalArgumentException("Null \'offset\' argument.");
    }
    this.labelOffset = offset;
    notifyListeners(new MarkerChangeEvent(this));
  }
  public void setLabelOffsetType(LengthAdjustmentType adj) {
    if(adj == null) {
      throw new IllegalArgumentException("Null \'adj\' argument.");
    }
    this.labelOffsetType = adj;
    notifyListeners(new MarkerChangeEvent(this));
  }
  public void setLabelPaint(Paint paint) {
    if(paint == null) {
      throw new IllegalArgumentException("Null \'paint\' argument.");
    }
    this.labelPaint = paint;
    notifyListeners(new MarkerChangeEvent(this));
  }
  public void setLabelTextAnchor(TextAnchor anchor) {
    if(anchor == null) {
      throw new IllegalArgumentException("Null \'anchor\' argument.");
    }
    this.labelTextAnchor = anchor;
    notifyListeners(new MarkerChangeEvent(this));
  }
  public void setOutlinePaint(Paint paint) {
    this.outlinePaint = paint;
    notifyListeners(new MarkerChangeEvent(this));
  }
  public void setOutlineStroke(Stroke stroke) {
    this.outlineStroke = stroke;
    notifyListeners(new MarkerChangeEvent(this));
  }
  public void setPaint(Paint paint) {
    if(paint == null) {
      throw new IllegalArgumentException("Null \'paint\' argument.");
    }
    this.paint = paint;
    notifyListeners(new MarkerChangeEvent(this));
  }
  public void setStroke(Stroke stroke) {
    if(stroke == null) {
      throw new IllegalArgumentException("Null \'stroke\' argument.");
    }
    this.stroke = stroke;
    notifyListeners(new MarkerChangeEvent(this));
  }
  private void writeObject(ObjectOutputStream stream) throws IOException {
    stream.defaultWriteObject();
    SerialUtilities.writePaint(this.paint, stream);
    SerialUtilities.writeStroke(this.stroke, stream);
    SerialUtilities.writePaint(this.outlinePaint, stream);
    SerialUtilities.writeStroke(this.outlineStroke, stream);
    SerialUtilities.writePaint(this.labelPaint, stream);
  }
}