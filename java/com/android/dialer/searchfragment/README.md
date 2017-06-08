# Dialer Search Ui

searchfragment/ contains all code relevant to loading, rendering and filtering
search results in both dialpad search and regular search.

## Loading

### On Device Contacts

On device contacts loading happens in SearchContactsCursorLoader. It is used in
conjunction with NewSearchFragment and Loader Callbacks to return a cursor from
cp2 containing all of the relevant info needed to rendering.

### Business Search

// TODO(calderwoodra)

### Google Directory Search

// TODO(calderwoodra)

## Rendering

NewSearchFragment, SearchAdapter, SearchContactViewHolder and
SearchCursorManager are used to render contact information. The fragment's
recyclerview, adapter and viewholder work as expected like a normal recyclerview
paradigm.

The are three things to note about rendering:

*   There are three data sources rendered: On device contacts, business search
    results and google directory results.
*   SearchContactsCursorLoader returns its cursor from cp2 and we filter/wrap it
    with SearchContactCursor to render useful results (see below).
*   SearchCursorManager is used to coalesce all three data sources to help with
    determining row count, row type and returning the correct data source for
    each position.

## Filtering

On device contacts are filtered using SearchContactCursor. We wrap the cursor
returned from SearchContactsCursorLoader in NewSearchFragment#onLoadFinished in
order to abstract away the filtering logic from the recyclerview adapter and
viewholders.

SearchContactCursor applies filtering in SearchContactCursor#filter to remove
duplicate phone numbers returned from cp2 and phone numbers that do not match
the given search query.

Filtering methods used are:

*   T9/dialpad search methods
    *   Initial match (957 matches [W]illiam [J]ohn [S]mith)
    *   Number + name match (1800946 matches [1800-Win]-A-Prize)
*   Numeric/dialpad search methods
    *   Simple number match (510333 matches [510-333]-7596)
    *   Country-code agnostic matching for E164 normalized numbers (9177 matches
        +65[9177]6930)
    *   Country-code agnostic matching (510333 matches 1-[510-333]-7596)
    *   Area-code agnostic matching (333 matches 510-[333]-7596)
*   Name/keyboard search methods:
    *   Simple name match (564 matches [Joh]n)
