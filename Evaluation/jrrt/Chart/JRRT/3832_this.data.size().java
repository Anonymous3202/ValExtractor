package org.jfree.data;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import org.jfree.chart.util.ObjectUtilities;
import org.jfree.data.general.Series;
import org.jfree.data.event.SeriesChangeEvent;
import org.jfree.data.general.SeriesException;

public class ComparableObjectSeries extends Series implements Cloneable, Serializable  {
  protected List data;
  private int maximumItemCount = Integer.MAX_VALUE;
  private boolean autoSort;
  private boolean allowDuplicateXValues;
  public ComparableObjectSeries(Comparable key) {
    this(key, true, true);
  }
  public ComparableObjectSeries(Comparable key, boolean autoSort, boolean allowDuplicateXValues) {
    super(key);
    this.data = new java.util.ArrayList();
    this.autoSort = autoSort;
    this.allowDuplicateXValues = allowDuplicateXValues;
  }
  protected ComparableObjectItem getDataItem(int index) {
    return (ComparableObjectItem)this.data.get(index);
  }
  protected ComparableObjectItem remove(int index) {
    ComparableObjectItem result = (ComparableObjectItem)this.data.remove(index);
    fireSeriesChanged();
    return result;
  }
  public ComparableObjectItem remove(Comparable x) {
    return remove(indexOf(x));
  }
  public boolean equals(Object obj) {
    if(obj == this) {
      return true;
    }
    if(!(obj instanceof ComparableObjectSeries)) {
      return false;
    }
    if(!super.equals(obj)) {
      return false;
    }
    ComparableObjectSeries that = (ComparableObjectSeries)obj;
    if(this.maximumItemCount != that.maximumItemCount) {
      return false;
    }
    if(this.autoSort != that.autoSort) {
      return false;
    }
    if(this.allowDuplicateXValues != that.allowDuplicateXValues) {
      return false;
    }
    if(!ObjectUtilities.equal(this.data, that.data)) {
      return false;
    }
    return true;
  }
  public boolean getAllowDuplicateXValues() {
    return this.allowDuplicateXValues;
  }
  public boolean getAutoSort() {
    return this.autoSort;
  }
  public int getItemCount() {
    return this.data.size();
  }
  public int getMaximumItemCount() {
    return this.maximumItemCount;
  }
  public int hashCode() {
    int result = super.hashCode();
    int count = getItemCount();
    if(count > 0) {
      ComparableObjectItem item = getDataItem(0);
      result = 29 * result + item.hashCode();
    }
    if(count > 1) {
      ComparableObjectItem item = getDataItem(count - 1);
      result = 29 * result + item.hashCode();
    }
    if(count > 2) {
      ComparableObjectItem item = getDataItem(count / 2);
      result = 29 * result + item.hashCode();
    }
    result = 29 * result + this.maximumItemCount;
    result = 29 * result + (this.autoSort ? 1 : 0);
    result = 29 * result + (this.allowDuplicateXValues ? 1 : 0);
    return result;
  }
  public int indexOf(Comparable x) {
    if(this.autoSort) {
      return Collections.binarySearch(this.data, new ComparableObjectItem(x, null));
    }
    else {
      for(int i = 0; i < this.data.size(); i++) {
        ComparableObjectItem item = (ComparableObjectItem)this.data.get(i);
        if(item.getComparable().equals(x)) {
          return i;
        }
      }
      return -1;
    }
  }
  protected void add(Comparable x, Object y) {
    add(x, y, true);
  }
  protected void add(Comparable x, Object y, boolean notify) {
    ComparableObjectItem item = new ComparableObjectItem(x, y);
    add(item, notify);
  }
  protected void add(ComparableObjectItem item, boolean notify) {
    if(item == null) {
      throw new IllegalArgumentException("Null \'item\' argument.");
    }
    if(this.autoSort) {
      int index = Collections.binarySearch(this.data, item);
      if(index < 0) {
        this.data.add(-index - 1, item);
      }
      else {
        if(this.allowDuplicateXValues) {
          int var_3832 = this.data.size();
          int size = var_3832;
          while(index < size && item.compareTo(this.data.get(index)) == 0){
            index++;
          }
          if(index < this.data.size()) {
            this.data.add(index, item);
          }
          else {
            this.data.add(item);
          }
        }
        else {
          throw new SeriesException("X-value already exists.");
        }
      }
    }
    else {
      if(!this.allowDuplicateXValues) {
        int index = indexOf(item.getComparable());
        if(index >= 0) {
          throw new SeriesException("X-value already exists.");
        }
      }
      this.data.add(item);
    }
    if(getItemCount() > this.maximumItemCount) {
      this.data.remove(0);
    }
    if(notify) {
      fireSeriesChanged();
    }
  }
  public void clear() {
    if(this.data.size() > 0) {
      this.data.clear();
      fireSeriesChanged();
    }
  }
  protected void delete(int start, int end) {
    for(int i = start; i <= end; i++) {
      this.data.remove(start);
    }
    fireSeriesChanged();
  }
  public void setMaximumItemCount(int maximum) {
    this.maximumItemCount = maximum;
    boolean dataRemoved = false;
    while(this.data.size() > maximum){
      this.data.remove(0);
      dataRemoved = true;
    }
    if(dataRemoved) {
      fireSeriesChanged();
    }
  }
  protected void update(Comparable x, Object y) {
    int index = indexOf(x);
    if(index < 0) {
      throw new SeriesException("No observation for x = " + x);
    }
    else {
      ComparableObjectItem item = getDataItem(index);
      item.setObject(y);
      fireSeriesChanged();
    }
  }
  protected void updateByIndex(int index, Object y) {
    ComparableObjectItem item = getDataItem(index);
    item.setObject(y);
    fireSeriesChanged();
  }
}