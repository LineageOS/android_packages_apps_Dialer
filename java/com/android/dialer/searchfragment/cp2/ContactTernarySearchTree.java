/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.dialer.searchfragment.cp2;

import android.support.v4.util.ArraySet;
import android.text.TextUtils;
import java.util.Set;

/** Ternary Search Tree for searching a list of contacts. */
public class ContactTernarySearchTree {

  private Node root;

  /**
   * Add {@code value} to all middle and end {@link Node#values} that correspond to {@code key}.
   *
   * <p>For example, if {@code key} were "FOO", {@code value} would be added to nodes "F", "O" and
   * "O". But if the traversal required visiting {@link Node#left} or {@link Node#right}, {@code
   * value} wouldn't be added to those nodes.
   */
  public void put(String key, int value) {
    if (TextUtils.isEmpty(key)) {
      return;
    }
    root = put(root, key, value, 0);
  }

  private Node put(Node node, String key, int value, int position) {
    char c = key.charAt(position);
    if (node == null) {
      node = new Node();
      node.key = c;
    }
    if (c < node.key) {
      node.left = put(node.left, key, value, position);
    } else if (c > node.key) {
      node.right = put(node.right, key, value, position);
    } else if (position < key.length() - 1) {
      node.values.add(value);
      node.mid = put(node.mid, key, value, position + 1);
    } else {
      node.values.add(value);
    }
    return node;
  }

  /** Returns true if {@code key} is contained in the trie. */
  public boolean contains(String key) {
    return !get(key).isEmpty();
  }

  /** Return value stored at Node (in this case, a set of integers). */
  public Set<Integer> get(String key) {
    Node x = get(root, key, 0);
    return x == null ? new ArraySet<>() : x.values;
  }

  private Node get(Node node, String key, int position) {
    if (node == null) {
      return null;
    }
    char c = key.charAt(position);
    if (c < node.key) {
      return get(node.left, key, position);
    } else if (c > node.key) {
      return get(node.right, key, position);
    } else if (position < key.length() - 1) {
      return get(node.mid, key, position + 1);
    } else {
      return node;
    }
  }

  /** Node in ternary search trie. Children are denoted as left, middle and right nodes. */
  private static class Node {
    private char key;
    private final Set<Integer> values = new ArraySet<>();

    private Node left;
    private Node mid;
    private Node right;
  }
}
