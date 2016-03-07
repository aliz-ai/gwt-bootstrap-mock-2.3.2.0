/*
 * Copyright 2007 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.gwt.user.client.ui;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gwt.core.client.JavaScriptObject;

/**
 * A prefix tree (aka trie).
 *
 */
class PrefixTree extends AbstractCollection<String> {

  /**
   * Iterates over the structure of a PrefixTree. No concurrency checks are
   * made. This Iterator will output anything new added to the tree if the new
   * entry is after the current position of the Iterator.
   *
   * This Iterator implementation is iterative and uses an internal stack object
   * to avoid call-stack limitations and invocation overhead.
   */
  private static class PrefixTreeIterator implements Iterator<String> {

    // Called from JSNI.
    private JavaScriptObject stack;

    /**
     * Constructor.
     *
     * @param tree The base of the PrefixTree to iterate over
     */
    public PrefixTreeIterator(PrefixTree tree) {
      init();
      addTree(tree, "");
    }

    public boolean hasNext() {
      // Have nextImpl peek at the next value that would be returned.
      return nextImpl(true) != null;
    }

    /**
     * {@inheritDoc} Wraps the native implementation with some sanity checking.
     */
    public String next() {
      final String toReturn = nextImpl(false);

      // A null response indicates that there was no data to be had.
      if (toReturn == null) {
        // Sanity check.
        if (!hasNext()) {
          throw new NoSuchElementException("No more elements in the iterator");
        } else {
          throw new RuntimeException(
              "nextImpl() returned null, but hasNext says otherwise");
        }
      }

      return toReturn;
    }

    public void remove() {
      throw new UnsupportedOperationException("PrefixTree does not support "
          + "removal.  Use clear()");
    }

    /**
     * Add a frame to the work stack.
     *
     * <pre>
     *  frame := {suffixNames, subtrees, prefix, index}
     *  suffixNames := All suffixes in the target PrefixTree
     *  subtrees := All subtrees in the target PrefixTree
     *  prefix := A string that next() will prepend to output from the frame
     *  index := Stores which suffix was last output
     * </pre>
     *
     * @param tree The tree to add
     * @param prefix The prefix to prepend to values in tree
     */
    private native void addTree(PrefixTree tree, String prefix) /*-{
      var suffixes = [];
      for (var suffix in tree.@com.google.gwt.user.client.ui.PrefixTree::suffixes) {
        // Ignore object properties that aren't colon-prepended keys
        if (suffix.indexOf(':') == 0) {
          suffixes.push(suffix);
        }
      }

      var frame = {
        suffixNames: suffixes,
        subtrees: tree.@com.google.gwt.user.client.ui.PrefixTree::subtrees,
        prefix: prefix,
        index: 0
      };

      var stack = this.@com.google.gwt.user.client.ui.PrefixTree$PrefixTreeIterator::stack;
      stack.push(frame);
    }-*/;

    /**
     * Initialize JSNI objects.
     */
    private native void init() /*-{
      this.@com.google.gwt.user.client.ui.PrefixTree$PrefixTreeIterator::stack = [];
    }-*/;

    /**
     * Access JSNI structures.
     *
     * @param peek If this is true, don't advance the iteration, just return the
     *          value that next() would return if it were called
     * @return The next object, or null if there is an error
     */
    private native String nextImpl(boolean peek) /*-{
      var stack = this.@com.google.gwt.user.client.ui.PrefixTree$PrefixTreeIterator::stack;
      var safe = @com.google.gwt.user.client.ui.PrefixTree::safe(Ljava/lang/String;)
      var unsafe = @com.google.gwt.user.client.ui.PrefixTree::unsafe(Ljava/lang/String;)

      // Put this in a while loop to handle descent into subtrees without recursion.
      while (stack.length > 0) {
        var frame = stack.pop();

        // Check to see if there are any remaining suffixes to output.
        if (frame.index < frame.suffixNames.length) {
          var toReturn = frame.prefix + unsafe(frame.suffixNames[frame.index]);

          if (!peek) {
            frame.index++;
          }

          // If the current frame has more suffixes, retain it on the stack.
          if (frame.index < frame.suffixNames.length) {
            stack.push(frame);

            // Otherwise, put all of the subtrees on the stack.
          } else {
            for (key in frame.subtrees) {
              if (key.indexOf(':') != 0) {
                continue;
              }
              var target = frame.prefix + unsafe(key);
              var subtree = frame.subtrees[key];
              this.@com.google.gwt.user.client.ui.PrefixTree$PrefixTreeIterator::addTree(Lcom/google/gwt/user/client/ui/PrefixTree;Ljava/lang/String;)(subtree, target);
            }
          }

          return toReturn;

       // Put all subframes on the stack, and return to top of loop.
       } else {
         for (var key in frame.subtrees) {
           if (key.indexOf(':') != 0) {
             continue;
           }
           var target = frame.prefix + unsafe(key);
           var subtree = frame.subtrees[key];

           this.@com.google.gwt.user.client.ui.PrefixTree$PrefixTreeIterator::addTree(Lcom/google/gwt/user/client/ui/PrefixTree;Ljava/lang/String;)(subtree, target);
         }
       }
     }

     // This would indicate that next() was called on an empty iterator.
     // Will throw an exception from next().
     return null;
    }-*/;
  }

  /**
   * Used by native methods to create an appropriately blessed PrefixTree.
   * 
   * @param prefixLength Smaller prefix length equals faster, more direct
   *          searches, at a cost of setup time
   * @return a newly constructed prefix tree
   */
  protected static PrefixTree createPrefixTree(int prefixLength) {
    return new PrefixTree(prefixLength);
  }

  /**
   *  Undo the operation performed by safe().
   *  
   *  @param s A String returned from safe()
   *  @return The original String passed into safe()
   */
  private static String unsafe(String s) {
    return s.substring(1);
  }

  /**
   * Stores the requested prefix length.
   */
  protected final int prefixLength;

  /**
   * Field to store terminal nodes in.
   */
  protected Set<String> suffixes;

  /**
   * Field to store subtrees in.
   */
  protected Map<String, PrefixTree> subtrees;

  /**
   * Store the number of elements contained by this PrefixTree and its
   * sub-trees.
   */
  protected int size = 0;

  /**
   * Constructor.
   */
  public PrefixTree() {
    this(2, null);
  }

  /**
   * Constructor.
   *
   * @param source Initialize from another collection
   */
  public PrefixTree(Collection<String> source) {
    this(2, source);
  }

  /**
   * Constructor.
   *
   * @param prefixLength Smaller prefix length equals faster, more direct
   *          searches, at a cost of setup time.
   */
  public PrefixTree(final int prefixLength) {
    this(prefixLength, null);
  }

  /**
   * Constructor.
   *
   * @param prefixLength Smaller prefix length equals faster, more direct
   *          searches, at a cost of setup time.
   * @param source Initialize from another collection
   */
  public PrefixTree(final int prefixLength, final Collection<String> source) {
    this.prefixLength = prefixLength;
    clear();

    if (source != null) {
      addAll(source);
    }
  }

  /**
   * Add a String to the PrefixTree.
   *
   * @param s The data to add
   * @return <code>true</code> if the string was added, <code>false</code>
   *         otherwise
   */
  @Override
  public boolean add(String s) {
    // This would indicate a mis-use of the code.
    if ((s == null) || (s.length() == 0)) {
      return false;
    }

    // Use <= so that strings that are exactly prefixLength long don't
    // require some kind of null token.
    if (s.length() <= prefixLength) {
      String safeKey = s;
      if (suffixes.contains(safeKey)) {
        return false;
      } else {
        // Each tree keeps a count of how large it and its children are.
        size++;

        suffixes.add(safeKey);
        return true;
      }

    // Add the string to the appropriate PrefixTree.
    } else {
      String prefix = s.substring(0, prefixLength);
      PrefixTree theTree;

      if (subtrees.containsKey(prefix)) {
        theTree = subtrees.get(prefix);
      } else {
        theTree = createPrefixTree(prefixLength << 1);
        subtrees.put(prefix, theTree);
      }

      String slice = s.substring(prefixLength);
      if (theTree.add(slice)) {
        // The size of the subtree increased, so we need to update the local count.
        size++;
        return true;
      } else {
        return false;
      }
    }
  }

  /**
   * Initialize native state.
   */
  @Override
  public void clear() {
    size = 0;
    subtrees = Maps.newHashMap();
    suffixes = Sets.newHashSet();
  }

  @Override
  public boolean contains(Object o) {
    if (o instanceof String) {
      return contains((String) o);
    } else {
      return false;
    }
  }

  public boolean contains(String s) {
    return (getSuggestions(s, 1)).contains(s);
  }

  /**
   * Retrieve suggestions from the PrefixTree. The number of items returned from
   * getSuggestions may slightly exceed <code>limit</code> so that all
   * suffixes and partial stems will be returned. This prevents the search space
   * from changing size if the PrefixTree is used in an interactive manner.
   * <br/> The returned List is guaranteed to be safe; changing its contents
   * will not affect the PrefixTree.
   *
   * @param search The prefix to search for
   * @param limit The desired number of results to retrieve
   * @return A List of suggestions
   */
  public List<String> getSuggestions(String search, int limit) {
    final List<String> toReturn = new ArrayList<String>();
    if ((search != null) && (limit > 0)) {
      suggestImpl(search, "", toReturn, limit);
    }
    return toReturn;
  }

  @Override
  public Iterator<String> iterator() {
    return new PrefixTreeIterator(this);
  }

  /**
   * Get the number of all elements contained within the PrefixTree.
   * 
   * @return the size of the prefix tree
   */
  @Override
  public int size() {
    return size;
  }

  protected void suggestImpl(String search, String prefix,
      Collection<String> output, int limit) {
    // Search is too big to be found in current tree, just recurse.
    if (search.length() > prefix.length() + prefixLength) {
      String key = search.substring(prefix.length(), prefix.length() + prefixLength);

      // Just pick the correct subtree, if it exists, and call suggestImpl.
      if (subtrees.containsKey(key)) {
        PrefixTree subtree = subtrees.get(key);
        String target = prefix + key;
        subtree.suggestImpl(search, target, output, limit);
      }

    // The answer can only exist in this tree's suffixes or subtree keys.
    } else {
     // Check local suffixes.
     for (String suffix : suffixes) {
       if (suffix.indexOf(':') != 0) {
         continue;
       }
       String target = prefix + suffix;
       if (target.indexOf(search) == 0) {
         output.add(target);
       }

       if (output.size() >= limit) {
         return;
       }
     }

     // Check the subtree keys.  If the key matches, that implies that all
     // elements of the subtree would match.
     for (String key : subtrees.keySet()) {
       if (key.indexOf(':') != 0) {
         continue;
       }
       String target = prefix + key;
       PrefixTree subtree = subtrees.get(key);

       // See if the prefix gives us a match.
       if (target.indexOf(search) == 0) {

         // Provide as many suggestions as will fit into the remaining limit.
         // If there is only one suggestion, include it.
         if ((subtree.size <= limit - output.size()) ||
             (subtree.size == 1)) {
           subtree.dump(output, target);

         // Otherwise, include as many answers as we can by truncating the suffix
         } else {

           // Always fully-specify suffixes.
           for (String suffix : subtree.suffixes) {
             if (suffix.indexOf(':') == 0) {
               output.add(target + suffix);
             }
           }

           // Give the keys of the subtree.
           for (String subkey : subtree.subtrees.keySet()) {
             if (subkey.indexOf(':') == 0) {
               output.add(target + subkey + "...");
             }
           }
         }
       }
     }
   }
  }

  /**
   * Put all contents of the PrefixTree into a Collection.
   * 
   * @param output the collection into which the prefixes will be dumped
   * @param prefix the prefix to filter with
   */
  private void dump(Collection<String> output, String prefix) {
    for (String s : this) {
      output.add(prefix + s);
    }
  }
}
