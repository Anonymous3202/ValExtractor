package org.mockito.internal.verification.checkers;
import java.util.List;
import org.mockito.exceptions.Reporter;
import org.mockito.internal.invocation.InvocationMatcher;
import org.mockito.internal.invocation.InvocationMarker;
import org.mockito.internal.invocation.InvocationsFinder;
import org.mockito.internal.reporting.Discrepancy;
import org.mockito.invocation.Invocation;
import org.mockito.invocation.Location;

public class NumberOfInvocationsChecker  {
  final private Reporter reporter;
  final private InvocationsFinder finder;
  final private InvocationMarker invocationMarker = new InvocationMarker();
  public NumberOfInvocationsChecker() {
    this(new Reporter(), new InvocationsFinder());
  }
  NumberOfInvocationsChecker(Reporter reporter, InvocationsFinder finder) {
    super();
    this.reporter = reporter;
    this.finder = finder;
  }
  public void check(List<Invocation> invocations, InvocationMatcher wanted, int wantedCount) {
    List<Invocation> actualInvocations = finder.findInvocations(invocations, wanted);
    int actualCount = actualInvocations.size();
    if(wantedCount > actualCount) {
      Location lastInvocation = finder.getLastLocation(actualInvocations);
      reporter.tooLittleActualInvocations(new Discrepancy(wantedCount, actualCount), wanted, lastInvocation);
    }
    else 
      if(wantedCount == 0 && actualCount > 0) {
        Location var_138 = actualInvocations.get(wantedCount).getLocation();
        Location firstUndesired = var_138;
        reporter.neverWantedButInvoked(wanted, firstUndesired);
      }
      else 
        if(wantedCount < actualCount) {
          Location firstUndesired = actualInvocations.get(wantedCount).getLocation();
          reporter.tooManyActualInvocations(wantedCount, actualCount, wanted, firstUndesired);
        }
    invocationMarker.markVerified(actualInvocations, wanted);
  }
}