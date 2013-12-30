#!/usr/bin/env python3

# Copyright (C) 2013 Xiao-Long Chen <chenxiaolong@cxl.epac.to>
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.

# Hacky way to merge Google's translations

import os
import re
import sys

names = [
  'dialer_settings_label',
  'dialer_hint_find_contact',
  'call_settings_label',
  'view_location_settings',
  'enable_reverse_lookup',
  'reverse_lookup_enabled',
  'google_caller_id_setting_title',
  'google_caller_id_setting__on',
  'google_caller_id_setting__off',
  'google_caller_id_settings_text',
  'local_search_directory_label',
  'local_search_setting_title',
  'local_search_setting_on',
  'local_search_setting_off',
  'local_search_settings_text'
]

MAGIC_BEGIN = 'BEGIN: do not remove'
MAGIC_END = 'END: do not remove'
COMMENT = '    <!-- Google Dialer (auto merged, do not edit) (%s) -->\n'

if len(sys.argv) != 2:
    print("Usage: %s [Google's res dir]" % __file__)
    sys.exit(1)

olddir = sys.argv[1]
newdir = os.path.join(os.path.dirname(os.path.realpath(__file__)), 'res')

for directory in os.listdir(olddir):
    if not directory.startswith('values'):
        continue

    strings_old = os.path.join(olddir, directory, 'strings.xml')
    if not os.path.exists(strings_old):
        print("Old %s does not have strings.xml" % directory)
        continue

    strings_new = os.path.join(newdir, directory, 'strings.xml')
    if not os.path.exists(strings_new):
        print("New %s does not have strings.xml" % directory)
        continue

    tempnames = dict()

    f = open(strings_old, 'r')
    lines = f.readlines()
    f.close()

    # Get Google's translations
    i = 0
    while i < len(lines):
        for j in names:
            if 'name="%s"' % j in lines[i]:
                temp = lines[i]
                while '</string>' not in lines[i]:
                    i += 1
                    temp += lines[i]

                if j == 'dialer_hint_find_contact':
                    temp = re.sub('dialer_hint_find_contact',
                                  'dialer_hint_find_contact_google', temp)
                tempnames[j] = temp

        i += 1

    f = open(strings_new, 'r')
    lines = f.readlines()
    f.close()

    # Remove old translations
    i = 0
    while i < len(lines):
        if MAGIC_BEGIN in lines[i]:
            while MAGIC_END not in lines[i]:
                del lines[i]
            if MAGIC_END in lines[i]:
                del lines[i]
            break
        i += 1

    # Merge new translations
    f = open(strings_new, 'w')

    first = True
    resources = False
    i = 0
    while i < len(lines):
        f.write(lines[i])
        if first:
            if 'resources' in lines[i]:
                first = False

                while '>' not in lines[i]:
                    i += 1
                    f.write(lines[i])

                f.write(COMMENT % MAGIC_BEGIN)

                for j in names:
                    if j in tempnames:
                        f.write(tempnames[j])

                f.write(COMMENT % MAGIC_END)

        i += 1

    f.close()
