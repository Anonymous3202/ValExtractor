package org.jfree.chart.util;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

public class LineUtilities  {
  public static boolean clipLine(Line2D line, Rectangle2D rect) {
    double x1 = line.getX1();
    double y1 = line.getY1();
    double x2 = line.getX2();
    double y2 = line.getY2();
    double minX = rect.getMinX();
    double maxX = rect.getMaxX();
    double minY = rect.getMinY();
    double maxY = rect.getMaxY();
    int f1 = rect.outcode(x1, y1);
    int f2 = rect.outcode(x2, y2);
    while((f1 | f2) != 0){
      if((f1 & f2) != 0) {
        return false;
      }
      double dx = (x2 - x1);
      double dy = (y2 - y1);
      if(f1 != 0) {
        if((f1 & Rectangle2D.OUT_LEFT) == Rectangle2D.OUT_LEFT && dx != 0.0D) {
          y1 = y1 + (minX - x1) * dy / dx;
          x1 = minX;
        }
        else 
          if((f1 & Rectangle2D.OUT_RIGHT) == Rectangle2D.OUT_RIGHT && dx != 0.0D) {
            y1 = y1 + (maxX - x1) * dy / dx;
            x1 = maxX;
          }
          else 
            if((f1 & Rectangle2D.OUT_BOTTOM) == Rectangle2D.OUT_BOTTOM && dy != 0.0D) {
              x1 = x1 + (maxY - y1) * dx / dy;
              y1 = maxY;
            }
            else {
              int var_3671 = Rectangle2D.OUT_TOP;
              if((f1 & var_3671) == Rectangle2D.OUT_TOP && dy != 0.0D) {
                x1 = x1 + (minY - y1) * dx / dy;
                y1 = minY;
              }
            }
        f1 = rect.outcode(x1, y1);
      }
      else 
        if(f2 != 0) {
          if((f2 & Rectangle2D.OUT_LEFT) == Rectangle2D.OUT_LEFT && dx != 0.0D) {
            y2 = y2 + (minX - x2) * dy / dx;
            x2 = minX;
          }
          else 
            if((f2 & Rectangle2D.OUT_RIGHT) == Rectangle2D.OUT_RIGHT && dx != 0.0D) {
              y2 = y2 + (maxX - x2) * dy / dx;
              x2 = maxX;
            }
            else 
              if((f2 & Rectangle2D.OUT_BOTTOM) == Rectangle2D.OUT_BOTTOM && dy != 0.0D) {
                x2 = x2 + (maxY - y2) * dx / dy;
                y2 = maxY;
              }
              else 
                if((f2 & Rectangle2D.OUT_TOP) == Rectangle2D.OUT_TOP && dy != 0.0D) {
                  x2 = x2 + (minY - y2) * dx / dy;
                  y2 = minY;
                }
          f2 = rect.outcode(x2, y2);
        }
    }
    line.setLine(x1, y1, x2, y2);
    return true;
  }
}