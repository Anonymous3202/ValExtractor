package org.jfree.chart.plot;
import java.util.Collections;

public class PieLabelDistributor extends AbstractPieLabelDistributor  {
  private double minGap = 4.0D;
  public PieLabelDistributor(int labelCount) {
    super();
  }
  public String toString() {
    StringBuffer result = new StringBuffer();
    for(int i = 0; i < this.labels.size(); i++) {
      result.append(getPieLabelRecord(i).toString()).append("\n");
    }
    return result.toString();
  }
  private boolean isOverlap() {
    double y = 0.0D;
    for(int i = 0; i < this.labels.size(); i++) {
      PieLabelRecord plr = getPieLabelRecord(i);
      if(y > plr.getLowerY()) {
        return true;
      }
      y = plr.getUpperY();
    }
    return false;
  }
  protected void adjustDownwards(double minY, double height) {
    for(int i = 0; i < this.labels.size() - 1; i++) {
      PieLabelRecord record0 = getPieLabelRecord(i);
      PieLabelRecord record1 = getPieLabelRecord(i + 1);
      if(record1.getLowerY() < record0.getUpperY()) {
        double var_2161 = record1.getLabelHeight();
        record1.setAllocatedY(Math.min(minY + height - var_2161 / 2.0D, record0.getUpperY() + this.minGap + record1.getLabelHeight() / 2.0D));
      }
    }
  }
  protected void adjustInwards() {
    int lower = 0;
    int upper = this.labels.size() - 1;
    while(upper > lower){
      if(lower < upper - 1) {
        PieLabelRecord r0 = getPieLabelRecord(lower);
        PieLabelRecord r1 = getPieLabelRecord(lower + 1);
        if(r1.getLowerY() < r0.getUpperY()) {
          double adjust = r0.getUpperY() - r1.getLowerY() + this.minGap;
          r1.setAllocatedY(r1.getAllocatedY() + adjust);
        }
      }
      PieLabelRecord r2 = getPieLabelRecord(upper - 1);
      PieLabelRecord r3 = getPieLabelRecord(upper);
      if(r2.getUpperY() > r3.getLowerY()) {
        double adjust = (r2.getUpperY() - r3.getLowerY()) + this.minGap;
        r3.setAllocatedY(r3.getAllocatedY() + adjust);
      }
      lower++;
      upper--;
    }
  }
  protected void adjustUpwards(double minY, double height) {
    for(int i = this.labels.size() - 1; i > 0; i--) {
      PieLabelRecord record0 = getPieLabelRecord(i);
      PieLabelRecord record1 = getPieLabelRecord(i - 1);
      if(record1.getUpperY() > record0.getLowerY()) {
        record1.setAllocatedY(Math.max(minY + record1.getLabelHeight() / 2.0D, record0.getLowerY() - this.minGap - record1.getLabelHeight() / 2.0D));
      }
    }
  }
  public void distributeLabels(double minY, double height) {
    sort();
    if(isOverlap()) {
      adjustDownwards(minY, height);
    }
    if(isOverlap()) {
      adjustUpwards(minY, height);
    }
    if(isOverlap()) {
      spreadEvenly(minY, height);
    }
  }
  public void sort() {
    Collections.sort(this.labels);
  }
  protected void spreadEvenly(double minY, double height) {
    double y = minY;
    double sumOfLabelHeights = 0.0D;
    for(int i = 0; i < this.labels.size(); i++) {
      sumOfLabelHeights += getPieLabelRecord(i).getLabelHeight();
    }
    double gap = height - sumOfLabelHeights;
    if(this.labels.size() > 1) {
      gap = gap / (this.labels.size() - 1);
    }
    for(int i = 0; i < this.labels.size(); i++) {
      PieLabelRecord record = getPieLabelRecord(i);
      y = y + record.getLabelHeight() / 2.0D;
      record.setAllocatedY(y);
      y = y + record.getLabelHeight() / 2.0D + gap;
    }
  }
}