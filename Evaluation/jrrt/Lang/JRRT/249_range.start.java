package org.apache.commons.lang3;
import java.io.Serializable;
import java.util.Iterator;
import java.util.NoSuchElementException;

final class CharRange implements Iterable<Character>, Serializable  {
  final private static long serialVersionUID = 8270183163158333422L;
  final private char start;
  final private char end;
  final private boolean negated;
  private transient String iToString;
  private CharRange(char start, char end, final boolean negated) {
    super();
    if(start > end) {
      final char temp = start;
      start = end;
      end = temp;
    }
    this.start = start;
    this.end = end;
    this.negated = negated;
  }
  public static CharRange is(final char ch) {
    return new CharRange(ch, ch, false);
  }
  public static CharRange isIn(final char start, final char end) {
    return new CharRange(start, end, false);
  }
  public static CharRange isNot(final char ch) {
    return new CharRange(ch, ch, true);
  }
  public static CharRange isNotIn(final char start, final char end) {
    return new CharRange(start, end, true);
  }
  @Override() public Iterator<Character> iterator() {
    return new CharacterIterator(this);
  }
  @Override() public String toString() {
    if(iToString == null) {
      final StringBuilder buf = new StringBuilder(4);
      if(isNegated()) {
        buf.append('^');
      }
      buf.append(start);
      if(start != end) {
        buf.append('-');
        buf.append(end);
      }
      iToString = buf.toString();
    }
    return iToString;
  }
  public boolean contains(final char ch) {
    return (ch >= start && ch <= end) != negated;
  }
  public boolean contains(final CharRange range) {
    if(range == null) {
      throw new IllegalArgumentException("The Range must not be null");
    }
    if(negated) {
      if(range.negated) {
        return start >= range.start && end <= range.end;
      }
      return range.end < start || range.start > end;
    }
    if(range.negated) {
      return start == 0 && end == Character.MAX_VALUE;
    }
    return start <= range.start && end >= range.end;
  }
  @Override() public boolean equals(final Object obj) {
    if(obj == this) {
      return true;
    }
    if(obj instanceof CharRange == false) {
      return false;
    }
    final CharRange other = (CharRange)obj;
    return start == other.start && end == other.end && negated == other.negated;
  }
  public boolean isNegated() {
    return negated;
  }
  public char getEnd() {
    return this.end;
  }
  public char getStart() {
    return this.start;
  }
  @Override() public int hashCode() {
    return 83 + start + 7 * end + (negated ? 1 : 0);
  }
  
  private static class CharacterIterator implements Iterator<Character>  {
    private char current;
    final private CharRange range;
    private boolean hasNext;
    private CharacterIterator(final CharRange r) {
      super();
      range = r;
      hasNext = true;
      if(range.negated) {
        char var_249 = range.start;
        if(var_249 == 0) {
          if(range.end == Character.MAX_VALUE) {
            hasNext = false;
          }
          else {
            current = (char)(range.end + 1);
          }
        }
        else {
          current = 0;
        }
      }
      else {
        current = range.start;
      }
    }
    @Override() public Character next() {
      if(hasNext == false) {
        throw new NoSuchElementException();
      }
      final char cur = current;
      prepareNext();
      return Character.valueOf(cur);
    }
    @Override() public boolean hasNext() {
      return hasNext;
    }
    private void prepareNext() {
      if(range.negated) {
        if(current == Character.MAX_VALUE) {
          hasNext = false;
        }
        else 
          if(current + 1 == range.start) {
            if(range.end == Character.MAX_VALUE) {
              hasNext = false;
            }
            else {
              current = (char)(range.end + 1);
            }
          }
          else {
            current = (char)(current + 1);
          }
      }
      else 
        if(current < range.end) {
          current = (char)(current + 1);
        }
        else {
          hasNext = false;
        }
    }
    @Override() public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}