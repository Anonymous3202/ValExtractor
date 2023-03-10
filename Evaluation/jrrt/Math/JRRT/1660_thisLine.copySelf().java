package org.apache.commons.math3.geometry.euclidean.twod;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.geometry.euclidean.oned.Euclidean1D;
import org.apache.commons.math3.geometry.euclidean.oned.Interval;
import org.apache.commons.math3.geometry.euclidean.oned.IntervalsSet;
import org.apache.commons.math3.geometry.euclidean.oned.OrientedPoint;
import org.apache.commons.math3.geometry.euclidean.oned.Vector1D;
import org.apache.commons.math3.geometry.partitioning.AbstractSubHyperplane;
import org.apache.commons.math3.geometry.partitioning.BSPTree;
import org.apache.commons.math3.geometry.partitioning.Hyperplane;
import org.apache.commons.math3.geometry.partitioning.Region;
import org.apache.commons.math3.geometry.partitioning.Region.Location;
import org.apache.commons.math3.geometry.partitioning.Side;
import org.apache.commons.math3.geometry.partitioning.SubHyperplane;
import org.apache.commons.math3.util.FastMath;

public class SubLine extends AbstractSubHyperplane<Euclidean2D, Euclidean1D>  {
  public SubLine(final Hyperplane<Euclidean2D> hyperplane, final Region<Euclidean1D> remainingRegion) {
    super(hyperplane, remainingRegion);
  }
  public SubLine(final Segment segment) {
    super(segment.getLine(), buildIntervalSet(segment.getStart(), segment.getEnd()));
  }
  public SubLine(final Vector2D start, final Vector2D end) {
    super(new Line(start, end), buildIntervalSet(start, end));
  }
  @Override() protected AbstractSubHyperplane<Euclidean2D, Euclidean1D> buildNew(final Hyperplane<Euclidean2D> hyperplane, final Region<Euclidean1D> remainingRegion) {
    return new SubLine(hyperplane, remainingRegion);
  }
  private static IntervalsSet buildIntervalSet(final Vector2D start, final Vector2D end) {
    final Line line = new Line(start, end);
    return new IntervalsSet(line.toSubSpace(start).getX(), line.toSubSpace(end).getX());
  }
  public List<Segment> getSegments() {
    final Line line = (Line)getHyperplane();
    final List<Interval> list = ((IntervalsSet)getRemainingRegion()).asList();
    final List<Segment> segments = new ArrayList<Segment>(list.size());
    for (final Interval interval : list) {
      final Vector2D start = line.toSpace(new Vector1D(interval.getInf()));
      final Vector2D end = line.toSpace(new Vector1D(interval.getSup()));
      segments.add(new Segment(start, end, line));
    }
    return segments;
  }
  @Override() public Side side(final Hyperplane<Euclidean2D> hyperplane) {
    final Line thisLine = (Line)getHyperplane();
    final Line otherLine = (Line)hyperplane;
    final Vector2D crossing = thisLine.intersection(otherLine);
    if(crossing == null) {
      final double global = otherLine.getOffset(thisLine);
      return (global < -1.0e-10D) ? Side.MINUS : ((global > 1.0e-10D) ? Side.PLUS : Side.HYPER);
    }
    final boolean direct = FastMath.sin(thisLine.getAngle() - otherLine.getAngle()) < 0;
    final Vector1D x = thisLine.toSubSpace(crossing);
    return getRemainingRegion().side(new OrientedPoint(x, direct));
  }
  @Override() public SplitSubHyperplane<Euclidean2D> split(final Hyperplane<Euclidean2D> hyperplane) {
    final Line thisLine = (Line)getHyperplane();
    final Line otherLine = (Line)hyperplane;
    final Vector2D crossing = thisLine.intersection(otherLine);
    if(crossing == null) {
      final double global = otherLine.getOffset(thisLine);
      return (global < -1.0e-10D) ? new SplitSubHyperplane<Euclidean2D>(null, this) : new SplitSubHyperplane<Euclidean2D>(this, null);
    }
    final boolean direct = FastMath.sin(thisLine.getAngle() - otherLine.getAngle()) < 0;
    final Vector1D x = thisLine.toSubSpace(crossing);
    final SubHyperplane<Euclidean1D> subPlus = new OrientedPoint(x, !direct).wholeHyperplane();
    final SubHyperplane<Euclidean1D> subMinus = new OrientedPoint(x, direct).wholeHyperplane();
    final BSPTree<Euclidean1D> splitTree = getRemainingRegion().getTree(false).split(subMinus);
    final BSPTree<Euclidean1D> plusTree = getRemainingRegion().isEmpty(splitTree.getPlus()) ? new BSPTree<Euclidean1D>(Boolean.FALSE) : new BSPTree<Euclidean1D>(subPlus, new BSPTree<Euclidean1D>(Boolean.FALSE), splitTree.getPlus(), null);
    final BSPTree<Euclidean1D> minusTree = getRemainingRegion().isEmpty(splitTree.getMinus()) ? new BSPTree<Euclidean1D>(Boolean.FALSE) : new BSPTree<Euclidean1D>(subMinus, new BSPTree<Euclidean1D>(Boolean.FALSE), splitTree.getMinus(), null);
    Line var_1660 = thisLine.copySelf();
    return new SplitSubHyperplane<Euclidean2D>(new SubLine(var_1660, new IntervalsSet(plusTree)), new SubLine(thisLine.copySelf(), new IntervalsSet(minusTree)));
  }
  public Vector2D intersection(final SubLine subLine, final boolean includeEndPoints) {
    Line line1 = (Line)getHyperplane();
    Line line2 = (Line)subLine.getHyperplane();
    Vector2D v2D = line1.intersection(line2);
    if(v2D == null) {
      return null;
    }
    Location loc1 = getRemainingRegion().checkPoint(line1.toSubSpace(v2D));
    Location loc2 = subLine.getRemainingRegion().checkPoint(line2.toSubSpace(v2D));
    if(includeEndPoints) {
      return ((loc1 != Location.OUTSIDE) && (loc2 != Location.OUTSIDE)) ? v2D : null;
    }
    else {
      return ((loc1 == Location.INSIDE) && (loc2 == Location.INSIDE)) ? v2D : null;
    }
  }
}