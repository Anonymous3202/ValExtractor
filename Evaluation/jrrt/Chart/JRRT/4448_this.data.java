package org.jfree.data.xy;
import java.io.Serializable;
import java.util.List;
import org.jfree.chart.event.DatasetChangeInfo;
import org.jfree.chart.util.ObjectUtilities;
import org.jfree.chart.util.PublicCloneable;
import org.jfree.data.event.DatasetChangeEvent;

public class XYIntervalSeriesCollection extends AbstractIntervalXYDataset implements IntervalXYDataset, PublicCloneable, Serializable  {
  private List data;
  public XYIntervalSeriesCollection() {
    super();
    this.data = new java.util.ArrayList();
  }
  public Comparable getSeriesKey(int series) {
    return getSeries(series).getKey();
  }
  public Number getEndX(int series, int item) {
    return new Double(getEndXValue(series, item));
  }
  public Number getEndY(int series, int item) {
    return new Double(getEndYValue(series, item));
  }
  public Number getStartX(int series, int item) {
    return new Double(getStartXValue(series, item));
  }
  public Number getStartY(int series, int item) {
    return new Double(getStartYValue(series, item));
  }
  public Number getX(int series, int item) {
    XYIntervalSeries s = (XYIntervalSeries)this.data.get(series);
    return s.getX(item);
  }
  public Number getY(int series, int item) {
    return new Double(getYValue(series, item));
  }
  public Object clone() throws CloneNotSupportedException {
    XYIntervalSeriesCollection clone = (XYIntervalSeriesCollection)super.clone();
    int seriesCount = getSeriesCount();
    clone.data = new java.util.ArrayList(seriesCount);
    for(int i = 0; i < this.data.size(); i++) {
      clone.data.set(i, getSeries(i).clone());
    }
    return clone;
  }
  public XYIntervalSeries getSeries(int series) {
    if((series < 0) || (series >= getSeriesCount())) {
      throw new IllegalArgumentException("Series index out of bounds");
    }
    return (XYIntervalSeries)this.data.get(series);
  }
  public boolean equals(Object obj) {
    if(obj == this) {
      return true;
    }
    if(!(obj instanceof XYIntervalSeriesCollection)) {
      return false;
    }
    XYIntervalSeriesCollection that = (XYIntervalSeriesCollection)obj;
    return ObjectUtilities.equal(this.data, that.data);
  }
  public double getEndXValue(int series, int item) {
    XYIntervalSeries s = (XYIntervalSeries)this.data.get(series);
    return s.getXHighValue(item);
  }
  public double getEndYValue(int series, int item) {
    XYIntervalSeries s = (XYIntervalSeries)this.data.get(series);
    return s.getYHighValue(item);
  }
  public double getStartXValue(int series, int item) {
    XYIntervalSeries s = (XYIntervalSeries)this.data.get(series);
    return s.getXLowValue(item);
  }
  public double getStartYValue(int series, int item) {
    XYIntervalSeries s = (XYIntervalSeries)this.data.get(series);
    return s.getYLowValue(item);
  }
  public double getYValue(int series, int item) {
    XYIntervalSeries s = (XYIntervalSeries)this.data.get(series);
    return s.getYValue(item);
  }
  public int getItemCount(int series) {
    return getSeries(series).getItemCount();
  }
  public int getSeriesCount() {
    return this.data.size();
  }
  public void addSeries(XYIntervalSeries series) {
    if(series == null) {
      throw new IllegalArgumentException("Null \'series\' argument.");
    }
    this.data.add(series);
    series.addChangeListener(this);
    fireDatasetChanged(new DatasetChangeInfo());
  }
  public void removeAllSeries() {
    for(int i = 0; i < this.data.size(); i++) {
      XYIntervalSeries series = (XYIntervalSeries)this.data.get(i);
      series.removeChangeListener(this);
    }
    this.data.clear();
    fireDatasetChanged(new DatasetChangeInfo());
  }
  public void removeSeries(int series) {
    if((series < 0) || (series >= getSeriesCount())) {
      throw new IllegalArgumentException("Series index out of bounds.");
    }
    XYIntervalSeries ts = (XYIntervalSeries)this.data.get(series);
    ts.removeChangeListener(this);
    this.data.remove(series);
    fireDatasetChanged(new DatasetChangeInfo());
  }
  public void removeSeries(XYIntervalSeries series) {
    if(series == null) {
      throw new IllegalArgumentException("Null \'series\' argument.");
    }
    List var_4448 = this.data;
    if(var_4448.contains(series)) {
      series.removeChangeListener(this);
      this.data.remove(series);
      fireDatasetChanged(new DatasetChangeInfo());
    }
  }
}