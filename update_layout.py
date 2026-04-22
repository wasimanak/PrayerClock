import re

with open('app/src/main/res/layout/activity_main.xml', 'r', encoding='utf-8') as f:
    content = f.read()

# Move AdView to footerSection
adview_regex = r'(<!-- AdMob Banner Ad -->\s*<com\.google\.android\.gms\.ads\.AdView[^>]+/>)'
adview_match = re.search(adview_regex, content)
adview_str = adview_match.group(1) if adview_match else ''

# Remove AdView from its current place
content = re.sub(adview_regex, '', content)

# Change ScrollView's inner LinearLayout to ConstraintLayout
old_inner_layout = '''        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:paddingBottom="16dp">'''

new_inner_layout = '''        <androidx.constraintlayout.widget.ConstraintLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:paddingBottom="16dp">'''

content = content.replace(old_inner_layout, new_inner_layout)

# In cardPrayerTimes, add constraints and 0dp height and min height
card_regex = r'(<LinearLayout\s+android:id="@+id/cardPrayerTimes".*?android:layout_height=")wrap_content(".*?>)'
card_replacement = r'\10dp\2\n        app:layout_constraintTop_toBottomOf="@id/headerSection"\n        app:layout_constraintBottom_toBottomOf="parent"\n        app:layout_constraintHeight_min="400dp"'
content = re.sub(card_regex, card_replacement, content, flags=re.DOTALL)

# Close ConstraintLayout instead of LinearLayout for the inner layout
inner_close_regex = r'(</LinearLayout>\s*)(</ScrollView>)'
content = re.sub(inner_close_regex, r'</androidx.constraintlayout.widget.ConstraintLayout>\n    \2', content)

# Change footerSection from ConstraintLayout to LinearLayout and insert AdView
footer_regex = r'(<!-- Footer Section -->\s*<androidx\.constraintlayout\.widget\.ConstraintLayout\s+android:id="@+id/footerSection"[^>]+>)'
footer_replacement = '''<!-- Footer Section -->
    <LinearLayout
        android:id="@+id/footerSection"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        app:layout_constraintBottom_toBottomOf="parent">'''
content = re.sub(footer_regex, footer_replacement, content)

# Clean up constraints in marqueeText
marquee_regex = r'(<TextView\s+android:id="@+id/marqueeText"[^>]+)app:layout_constraintEnd_toEndOf="parent"\s*app:layout_constraintStart_toStartOf="parent"\s*app:layout_constraintTop_toTopOf="parent"\s*(/>)'
content = re.sub(marquee_regex, r'\1 \2', content)

# Insert AdView before BottomNav
bottom_nav_regex = r'(<!-- Custom Professional Bottom Bar -->)'
content = re.sub(bottom_nav_regex, adview_str + '\n\n        ' + r'\1', content)

# Remove constraints from bottom nav include
bottom_nav_include_regex = r'(<include\s+layout="@layout/layout_bottom_nav"\s+android:layout_width="match_parent"\s+android:layout_height="wrap_content")\s*app:layout_constraintTop_toBottomOf="@id/marqueeText"\s*app:layout_constraintBottom_toBottomOf="parent"\s*(/>)'
content = re.sub(bottom_nav_include_regex, r'\1 \2', content)

# Change footerSection close tag
content = content.replace('</androidx.constraintlayout.widget.ConstraintLayout>\n\n</androidx.constraintlayout.widget.ConstraintLayout>', '</LinearLayout>\n\n</androidx.constraintlayout.widget.ConstraintLayout>')

with open('app/src/main/res/layout/activity_main.xml', 'w', encoding='utf-8') as f:
    f.write(content)
print('Done!')
