package org.apache.commons.math3.geometry.euclidean.threed;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.geometry.euclidean.oned.Interval;
import org.apache.commons.math3.geometry.euclidean.oned.IntervalsSet;
import org.apache.commons.math3.geometry.euclidean.oned.Vector1D;
import org.apache.commons.math3.geometry.partitioning.Region.Location;

public class SubLine  {
  final private Line line;
  final private IntervalsSet remainingRegion;
  public SubLine(final Line line, final IntervalsSet remainingRegion) {
    super();
    this.line = line;
    this.remainingRegion = remainingRegion;
  }
  public SubLine(final Segment segment) throws MathIllegalArgumentException {
    this(segment.getLine(), buildIntervalSet(segment.getStart(), segment.getEnd()));
  }
  public SubLine(final Vector3D start, final Vector3D end) throws MathIllegalArgumentException {
    this(new Line(start, end), buildIntervalSet(start, end));
  }
  private static IntervalsSet buildIntervalSet(final Vector3D start, final Vector3D end) throws MathIllegalArgumentException {
    final Line line = new Line(start, end);
    return new IntervalsSet(line.toSubSpace(start).getX(), line.toSubSpace(end).getX());
  }
  public List<Segment> getSegments() {
    final List<Interval> list = remainingRegion.asList();
    final List<Segment> segments = new ArrayList<Segment>(list.size());
    for (final Interval interval : list) {
      final Vector3D start = line.toSpace(new Vector1D(interval.getInf()));
      final Vector3D end = line.toSpace(new Vector1D(interval.getSup()));
      segments.add(new Segment(start, end, line));
    }
    return segments;
  }
  public Vector3D intersection(final SubLine subLine, final boolean includeEndPoints) {
    Vector3D v1D = line.intersection(subLine.line);
    if(v1D == null) {
      return null;
    }
    Location loc1 = remainingRegion.checkPoint(line.toSubSpace(v1D));
    Location loc2 = subLine.remainingRegion.checkPoint(subLine.line.toSubSpace(v1D));
    if(includeEndPoints) {
      Location var_1471 = Location.OUTSIDE;
      return ((loc1 != var_1471) && (loc2 != Location.OUTSIDE)) ? v1D : null;
    }
    else {
      return ((loc1 == Location.INSIDE) && (loc2 == Location.INSIDE)) ? v1D : null;
    }
  }
}