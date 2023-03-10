package org.jfree.data.time;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import org.jfree.chart.util.ObjectUtilities;
import org.jfree.data.general.Series;
import org.jfree.data.event.SeriesChangeEvent;
import org.jfree.data.general.SeriesException;

public class TimeSeries extends Series implements Cloneable, Serializable  {
  final private static long serialVersionUID = -5032960206869675528L;
  final protected static String DEFAULT_DOMAIN_DESCRIPTION = "Time";
  final protected static String DEFAULT_RANGE_DESCRIPTION = "Value";
  private String domain;
  private String range;
  protected Class timePeriodClass;
  protected List data;
  private int maximumItemCount;
  private long maximumItemAge;
  private double minY;
  private double maxY;
  public TimeSeries(Comparable name) {
    this(name, DEFAULT_DOMAIN_DESCRIPTION, DEFAULT_RANGE_DESCRIPTION);
  }
  public TimeSeries(Comparable name, String domain, String range) {
    super(name);
    this.domain = domain;
    this.range = range;
    this.timePeriodClass = null;
    this.data = new java.util.ArrayList();
    this.maximumItemCount = Integer.MAX_VALUE;
    this.maximumItemAge = Long.MAX_VALUE;
    this.minY = Double.NaN;
    this.maxY = Double.NaN;
  }
  public Class getTimePeriodClass() {
    return this.timePeriodClass;
  }
  public Collection getTimePeriods() {
    Collection result = new java.util.ArrayList();
    for(int i = 0; i < getItemCount(); i++) {
      result.add(getTimePeriod(i));
    }
    return result;
  }
  public Collection getTimePeriodsUniqueToOtherSeries(TimeSeries series) {
    Collection result = new java.util.ArrayList();
    for(int i = 0; i < series.getItemCount(); i++) {
      RegularTimePeriod period = series.getTimePeriod(i);
      int index = getIndex(period);
      if(index < 0) {
        result.add(period);
      }
    }
    return result;
  }
  public List getItems() {
    return Collections.unmodifiableList(this.data);
  }
  public Number getValue(int index) {
    return getRawDataItem(index).getValue();
  }
  public Number getValue(RegularTimePeriod period) {
    int index = getIndex(period);
    if(index >= 0) {
      return getValue(index);
    }
    else {
      return null;
    }
  }
  public Object clone() throws CloneNotSupportedException {
    TimeSeries clone = (TimeSeries)super.clone();
    clone.data = (List)ObjectUtilities.deepClone(this.data);
    return clone;
  }
  public RegularTimePeriod getNextTimePeriod() {
    RegularTimePeriod last = getTimePeriod(getItemCount() - 1);
    return last.next();
  }
  public RegularTimePeriod getTimePeriod(int index) {
    return getRawDataItem(index).getPeriod();
  }
  public String getDomainDescription() {
    return this.domain;
  }
  public String getRangeDescription() {
    return this.range;
  }
  public TimeSeries addAndOrUpdate(TimeSeries series) {
    TimeSeries overwritten = new TimeSeries("Overwritten values from: " + getKey());
    for(int i = 0; i < series.getItemCount(); i++) {
      TimeSeriesDataItem item = series.getRawDataItem(i);
      TimeSeriesDataItem oldItem = addOrUpdate(item.getPeriod(), item.getValue());
      if(oldItem != null) {
        overwritten.add(oldItem);
      }
    }
    return overwritten;
  }
  public TimeSeries createCopy(int start, int end) throws CloneNotSupportedException {
    if(start < 0) {
      throw new IllegalArgumentException("Requires start >= 0.");
    }
    if(end < start) {
      throw new IllegalArgumentException("Requires start <= end.");
    }
    TimeSeries copy = (TimeSeries)super.clone();
    copy.minY = Double.NaN;
    copy.maxY = Double.NaN;
    copy.data = new java.util.ArrayList();
    if(this.data.size() > 0) {
      for(int index = start; index <= end; index++) {
        TimeSeriesDataItem item = (TimeSeriesDataItem)this.data.get(index);
        TimeSeriesDataItem clone = (TimeSeriesDataItem)item.clone();
        try {
          copy.add(clone);
        }
        catch (SeriesException e) {
          e.printStackTrace();
        }
      }
    }
    return copy;
  }
  public TimeSeries createCopy(RegularTimePeriod start, RegularTimePeriod end) throws CloneNotSupportedException {
    if(start == null) {
      throw new IllegalArgumentException("Null \'start\' argument.");
    }
    if(end == null) {
      throw new IllegalArgumentException("Null \'end\' argument.");
    }
    if(start.compareTo(end) > 0) {
      throw new IllegalArgumentException("Requires start on or before end.");
    }
    boolean emptyRange = false;
    int startIndex = getIndex(start);
    if(startIndex < 0) {
      startIndex = -(startIndex + 1);
      if(startIndex == this.data.size()) {
        emptyRange = true;
      }
    }
    int endIndex = getIndex(end);
    if(endIndex < 0) {
      endIndex = -(endIndex + 1);
      endIndex = endIndex - 1;
    }
    if((endIndex < 0) || (endIndex < startIndex)) {
      emptyRange = true;
    }
    if(emptyRange) {
      TimeSeries copy = (TimeSeries)super.clone();
      copy.data = new java.util.ArrayList();
      return copy;
    }
    else {
      return createCopy(startIndex, endIndex);
    }
  }
  public TimeSeriesDataItem addOrUpdate(RegularTimePeriod period, double value) {
    return addOrUpdate(period, new Double(value));
  }
  public TimeSeriesDataItem addOrUpdate(RegularTimePeriod period, Number value) {
    return addOrUpdate(new TimeSeriesDataItem(period, value));
  }
  public TimeSeriesDataItem addOrUpdate(TimeSeriesDataItem item) {
    if(item == null) {
      throw new IllegalArgumentException("Null \'period\' argument.");
    }
    Class periodClass = item.getPeriod().getClass();
    if(this.timePeriodClass == null) {
      this.timePeriodClass = periodClass;
    }
    else 
      if(!this.timePeriodClass.equals(periodClass)) {
        String msg = "You are trying to add data where the time " + "period class is " + periodClass.getName() + ", but the TimeSeries is expecting an instance of " + this.timePeriodClass.getName() + ".";
        throw new SeriesException(msg);
      }
    TimeSeriesDataItem overwritten = null;
    int index = Collections.binarySearch(this.data, item);
    if(index >= 0) {
      TimeSeriesDataItem existing = (TimeSeriesDataItem)this.data.get(index);
      overwritten = (TimeSeriesDataItem)existing.clone();
      boolean iterate = false;
      Number oldYN = existing.getValue();
      double oldY = oldYN != null ? oldYN.doubleValue() : Double.NaN;
      if(!Double.isNaN(oldY)) {
        iterate = oldY <= this.minY || oldY >= this.maxY;
      }
      existing.setValue(item.getValue());
      if(iterate) {
        findBoundsByIteration();
      }
      else 
        if(item.getValue() != null) {
          double yy = item.getValue().doubleValue();
          this.minY = minIgnoreNaN(this.minY, yy);
          this.maxY = minIgnoreNaN(this.maxY, yy);
        }
    }
    else {
      item = (TimeSeriesDataItem)item.clone();
      this.data.add(-index - 1, item);
      updateBoundsForAddedItem(item);
      if(getItemCount() > this.maximumItemCount) {
        TimeSeriesDataItem d = (TimeSeriesDataItem)this.data.remove(0);
        updateBoundsForRemovedItem(d);
      }
    }
    removeAgedItems(false);
    fireSeriesChanged();
    return overwritten;
  }
  public TimeSeriesDataItem getDataItem(int index) {
    TimeSeriesDataItem item = (TimeSeriesDataItem)this.data.get(index);
    return (TimeSeriesDataItem)item.clone();
  }
  public TimeSeriesDataItem getDataItem(RegularTimePeriod period) {
    int index = getIndex(period);
    if(index >= 0) {
      return getDataItem(index);
    }
    else {
      return null;
    }
  }
  TimeSeriesDataItem getRawDataItem(int index) {
    return (TimeSeriesDataItem)this.data.get(index);
  }
  TimeSeriesDataItem getRawDataItem(RegularTimePeriod period) {
    int index = getIndex(period);
    if(index >= 0) {
      return (TimeSeriesDataItem)this.data.get(index);
    }
    else {
      return null;
    }
  }
  public boolean equals(Object obj) {
    if(obj == this) {
      return true;
    }
    if(!(obj instanceof TimeSeries)) {
      return false;
    }
    TimeSeries that = (TimeSeries)obj;
    if(!ObjectUtilities.equal(getDomainDescription(), that.getDomainDescription())) {
      return false;
    }
    if(!ObjectUtilities.equal(getRangeDescription(), that.getRangeDescription())) {
      return false;
    }
    if(!ObjectUtilities.equal(this.timePeriodClass, that.timePeriodClass)) {
      return false;
    }
    if(getMaximumItemAge() != that.getMaximumItemAge()) {
      return false;
    }
    if(getMaximumItemCount() != that.getMaximumItemCount()) {
      return false;
    }
    int count = getItemCount();
    if(count != that.getItemCount()) {
      return false;
    }
    if(!ObjectUtilities.equal(this.data, that.data)) {
      return false;
    }
    return super.equals(obj);
  }
  public double getMaxY() {
    return this.maxY;
  }
  public double getMinY() {
    return this.minY;
  }
  private double maxIgnoreNaN(double a, double b) {
    if(Double.isNaN(a)) {
      return b;
    }
    else {
      if(Double.isNaN(b)) {
        return a;
      }
      else {
        return Math.max(a, b);
      }
    }
  }
  private double minIgnoreNaN(double a, double b) {
    if(Double.isNaN(a)) {
      return b;
    }
    else {
      if(Double.isNaN(b)) {
        return a;
      }
      else {
        return Math.min(a, b);
      }
    }
  }
  public int getIndex(RegularTimePeriod period) {
    if(period == null) {
      throw new IllegalArgumentException("Null \'period\' argument.");
    }
    TimeSeriesDataItem dummy = new TimeSeriesDataItem(period, Integer.MIN_VALUE);
    return Collections.binarySearch(this.data, dummy);
  }
  public int getItemCount() {
    return this.data.size();
  }
  public int getMaximumItemCount() {
    return this.maximumItemCount;
  }
  public int hashCode() {
    int result = super.hashCode();
    result = 29 * result + (this.domain != null ? this.domain.hashCode() : 0);
    result = 29 * result + (this.range != null ? this.range.hashCode() : 0);
    result = 29 * result + (this.timePeriodClass != null ? this.timePeriodClass.hashCode() : 0);
    int count = getItemCount();
    if(count > 0) {
      TimeSeriesDataItem item = getRawDataItem(0);
      result = 29 * result + item.hashCode();
    }
    if(count > 1) {
      TimeSeriesDataItem item = getRawDataItem(count - 1);
      result = 29 * result + item.hashCode();
    }
    if(count > 2) {
      TimeSeriesDataItem item = getRawDataItem(count / 2);
      result = 29 * result + item.hashCode();
    }
    result = 29 * result + this.maximumItemCount;
    result = 29 * result + (int)this.maximumItemAge;
    return result;
  }
  public long getMaximumItemAge() {
    return this.maximumItemAge;
  }
  public void add(RegularTimePeriod period, double value) {
    add(period, value, true);
  }
  public void add(RegularTimePeriod period, double value, boolean notify) {
    TimeSeriesDataItem item = new TimeSeriesDataItem(period, value);
    add(item, notify);
  }
  public void add(RegularTimePeriod period, Number value) {
    add(period, value, true);
  }
  public void add(RegularTimePeriod period, Number value, boolean notify) {
    TimeSeriesDataItem item = new TimeSeriesDataItem(period, value);
    add(item, notify);
  }
  public void add(TimeSeriesDataItem item) {
    add(item, true);
  }
  public void add(TimeSeriesDataItem item, boolean notify) {
    if(item == null) {
      throw new IllegalArgumentException("Null \'item\' argument.");
    }
    item = (TimeSeriesDataItem)item.clone();
    Class c = item.getPeriod().getClass();
    if(this.timePeriodClass == null) {
      this.timePeriodClass = c;
    }
    else 
      if(!this.timePeriodClass.equals(c)) {
        StringBuffer b = new StringBuffer();
        b.append("You are trying to add data where the time period class ");
        b.append("is ");
        b.append(item.getPeriod().getClass().getName());
        b.append(", but the TimeSeries is expecting an instance of ");
        b.append(this.timePeriodClass.getName());
        b.append(".");
        throw new SeriesException(b.toString());
      }
    boolean added = false;
    int count = getItemCount();
    if(count == 0) {
      this.data.add(item);
      added = true;
    }
    else {
      RegularTimePeriod last = getTimePeriod(getItemCount() - 1);
      if(item.getPeriod().compareTo(last) > 0) {
        this.data.add(item);
        added = true;
      }
      else {
        int index = Collections.binarySearch(this.data, item);
        if(index < 0) {
          this.data.add(-index - 1, item);
          added = true;
        }
        else {
          StringBuffer b = new StringBuffer();
          b.append("You are attempting to add an observation for ");
          b.append("the time period ");
          b.append(item.getPeriod().toString());
          b.append(" but the series already contains an observation");
          b.append(" for that time period. Duplicates are not ");
          b.append("permitted.  Try using the addOrUpdate() method.");
          throw new SeriesException(b.toString());
        }
      }
    }
    if(added) {
      updateBoundsForAddedItem(item);
      if(getItemCount() > this.maximumItemCount) {
        TimeSeriesDataItem d = (TimeSeriesDataItem)this.data.remove(0);
        updateBoundsForRemovedItem(d);
      }
      removeAgedItems(false);
      if(notify) {
        fireSeriesChanged();
      }
    }
  }
  public void clear() {
    if(this.data.size() > 0) {
      this.data.clear();
      this.timePeriodClass = null;
      this.minY = Double.NaN;
      this.maxY = Double.NaN;
      fireSeriesChanged();
    }
  }
  public void delete(int start, int end) {
    delete(start, end, true);
  }
  public void delete(int start, int end, boolean notify) {
    if(end < start) {
      throw new IllegalArgumentException("Requires start <= end.");
    }
    for(int i = 0; i <= (end - start); i++) {
      this.data.remove(start);
    }
    findBoundsByIteration();
    if(this.data.isEmpty()) {
      this.timePeriodClass = null;
    }
    if(notify) {
      fireSeriesChanged();
    }
  }
  public void delete(RegularTimePeriod period) {
    int index = getIndex(period);
    if(index >= 0) {
      TimeSeriesDataItem item = (TimeSeriesDataItem)this.data.remove(index);
      updateBoundsForRemovedItem(item);
      if(this.data.isEmpty()) {
        this.timePeriodClass = null;
      }
      fireSeriesChanged();
    }
  }
  private void findBoundsByIteration() {
    this.minY = Double.NaN;
    this.maxY = Double.NaN;
    Iterator iterator = this.data.iterator();
    while(iterator.hasNext()){
      TimeSeriesDataItem item = (TimeSeriesDataItem)iterator.next();
      updateBoundsForAddedItem(item);
    }
  }
  public void removeAgedItems(boolean notify) {
    if(getItemCount() > 1) {
      long latest = getTimePeriod(getItemCount() - 1).getSerialIndex();
      boolean removed = false;
      while((latest - getTimePeriod(0).getSerialIndex()) > this.maximumItemAge){
        this.data.remove(0);
        removed = true;
      }
      if(removed) {
        findBoundsByIteration();
        if(notify) {
          fireSeriesChanged();
        }
      }
    }
  }
  public void removeAgedItems(long latest, boolean notify) {
    if(this.data.isEmpty()) {
      return ;
    }
    long index = Long.MAX_VALUE;
    try {
      Method m = RegularTimePeriod.class.getDeclaredMethod("createInstance", new Class[]{ Class.class, Date.class, TimeZone.class } );
      RegularTimePeriod newest = (RegularTimePeriod)m.invoke(this.timePeriodClass, new Object[]{ this.timePeriodClass, new Date(latest), TimeZone.getDefault() } );
      index = newest.getSerialIndex();
    }
    catch (NoSuchMethodException e) {
      e.printStackTrace();
    }
    catch (IllegalAccessException e) {
      e.printStackTrace();
    }
    catch (InvocationTargetException e) {
      e.printStackTrace();
    }
    boolean removed = false;
    while(getItemCount() > 0 && (index - getTimePeriod(0).getSerialIndex()) > this.maximumItemAge){
      this.data.remove(0);
      removed = true;
    }
    if(removed) {
      findBoundsByIteration();
      if(notify) {
        fireSeriesChanged();
      }
    }
  }
  public void setDomainDescription(String description) {
    String var_4151 = this.domain;
    String old = var_4151;
    this.domain = description;
    firePropertyChange("Domain", old, description);
  }
  public void setMaximumItemAge(long periods) {
    if(periods < 0) {
      throw new IllegalArgumentException("Negative \'periods\' argument.");
    }
    this.maximumItemAge = periods;
    removeAgedItems(true);
  }
  public void setMaximumItemCount(int maximum) {
    if(maximum < 0) {
      throw new IllegalArgumentException("Negative \'maximum\' argument.");
    }
    this.maximumItemCount = maximum;
    int count = this.data.size();
    if(count > maximum) {
      delete(0, count - maximum - 1);
    }
  }
  public void setRangeDescription(String description) {
    String old = this.range;
    this.range = description;
    firePropertyChange("Range", old, description);
  }
  public void update(int index, Number value) {
    TimeSeriesDataItem item = (TimeSeriesDataItem)this.data.get(index);
    boolean iterate = false;
    Number oldYN = item.getValue();
    if(oldYN != null) {
      double oldY = oldYN.doubleValue();
      if(!Double.isNaN(oldY)) {
        iterate = oldY <= this.minY || oldY >= this.maxY;
      }
    }
    item.setValue(value);
    if(iterate) {
      findBoundsByIteration();
    }
    else 
      if(value != null) {
        double yy = value.doubleValue();
        this.minY = minIgnoreNaN(this.minY, yy);
        this.maxY = maxIgnoreNaN(this.maxY, yy);
      }
    fireSeriesChanged();
  }
  public void update(RegularTimePeriod period, Number value) {
    TimeSeriesDataItem temp = new TimeSeriesDataItem(period, value);
    int index = Collections.binarySearch(this.data, temp);
    if(index < 0) {
      throw new SeriesException("There is no existing value for the " + "specified \'period\'.");
    }
    update(index, value);
  }
  private void updateBoundsForAddedItem(TimeSeriesDataItem item) {
    Number yN = item.getValue();
    if(item.getValue() != null) {
      double y = yN.doubleValue();
      this.minY = minIgnoreNaN(this.minY, y);
      this.maxY = maxIgnoreNaN(this.maxY, y);
    }
  }
  private void updateBoundsForRemovedItem(TimeSeriesDataItem item) {
    Number yN = item.getValue();
    if(yN != null) {
      double y = yN.doubleValue();
      if(!Double.isNaN(y)) {
        if(y <= this.minY || y >= this.maxY) {
          findBoundsByIteration();
        }
      }
    }
  }
}