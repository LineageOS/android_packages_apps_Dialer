# How to use this directory

First thing to note, it's imperative that the application and all activities
inherit from the styles outlined in theme/base. If an Activity doesn\'t specify
a style for it's theme, it automatically inherits one from the Application. And
this hierarchy continues: Application > Activity > Fragment > View > View > ...

## theme/base

This is where the base application themes, activity themes and application wide
style attributes live.

What is an attribute? There are three types (depending on how you want to define
them):

*   Legacy Android attributes (android:colorPrimary). These are defined by the
    Android framework and they exist to allow developers easily custom Android
    provided widgets.

*   Appcompat Android attributes (colorPrimary). There are also defined by the
    Android framework but they only exist to customize AppCompat widgets. After
    the framework was more mature, the team realized that they needed to add
    more customization to their widgets, so they created the AppCompat variant
    with all of the same attributes plus some. *Note:* Unfortunately our app
    uses both Legacy Widgets and AppCompat widgets, so when you define an
    AppCompat attribute in a style, be sure to also define the Legacy version as
    well if it exists.

*   Custom application attributes (colorIcon). It goes without saying that the
    names can't collide with the framework attributes. These attributes server
    to satisfy what the framework doesn't. For example, the framework doesn't
    provide an attribute to tint all of your ImageViews (why would it?), so we
    created the colorIcon attribute to apply to all ImageViews that show quantum
    icons/assets that need to be tinted.

Styles in this package follow a naming convention of inheritance:

*   Dialer. and Dialer.NoActionBar are the two root themes that should be used
    to add sdk specific features (like coloring the Android nav bar).

*   Dialer.ThemeBase.ActionBar and Dialer.ThemeBase.NoActionBar are great
    starting points for Activity style's to inherit from if they need specific
    customizations.

*   Dialer.ThemeBase.ActionBar.* and Dialer.ThemeBase.NoActionBar.* are
    specialized app themes intended to change the entire look of the app. For
    example, Dialer.ThemeBase.NoActionBar.Dark is used for a dark mode theme. If
    you create a custom theme for an activity, be sure your customization will
    work for all themes. See dialer/dialpadview/theme for an example.

## theme/common

This is a dumping ground for shared resources. Some examples of what should live
here:

*   Colors that can't be theme'd (there aren't many of those, so you probably
    won't do that).
*   Drawables, images, animations, dimensions, styles, ect. that can be (or are)
    used throughout the entire app without the need for customization. If you
    want to customize a specific style for one use case, feel free to override
    it and store it in your own resource directory.

## theme/private

This package is only visible to theme/base. Things you should never do:

*   Reference anything in here.
*   Duplicate the resources from this directory into another.

Things you should do:

*   Add colors that are common throughout the entire app and need be themed. For
    example, text colors, background colors and app primary and accent colors.
    Each color you add needs to exist in each color_*.xml file where *
    represents an app theme like 'dark' or 'light'.
