# BlushFit Android Fitness Tracker

BlushFit is a Kotlin Android application created for the NCC Level 5 Mobile App
Development assignment. It supports registration and login, offline workout
logging, daily calorie goals, summary statistics, geolocation and PHP/MySQL sync.

## Run the Android app

1. Open this folder in Android Studio.
2. Allow Gradle to sync and run the `app` configuration on an Android emulator.
3. Choose **Continue offline** to test immediately, or configure the backend below.
4. Long-press a workout card to remove it.

## Configure the PHP/MySQL backend

1. Install XAMPP and start Apache and MySQL.
2. Import `backend/database.sql` in phpMyAdmin.
3. Copy the contents of `backend` to `C:\xampp\htdocs\fitness_api`.
4. Update the credentials in `backend/db_connect.php` if necessary.
5. The emulator uses `http://10.0.2.2/fitness_api/`, already configured in
   `ApiClient.kt`. For a physical phone, replace `10.0.2.2` with the computer's
   local network IP address.

## Assignment functionality

- RecyclerView with Running, Cycling and Weightlifting subclasses
- FloatingActionButton and validated add-workout dialog
- Live daily calorie, estimated-step and goal calculations
- Seven-day analytics, activity breakdown and personalised insights
- Extended Yoga activity in addition to the three required activities
- Runtime location permission and optional workout coordinates
- Local SharedPreferences storage for offline demonstrations
- Registration/login with password hashing and prepared PDO statements
- MySQL workout storage and retrieval through PHP endpoints
- MySQL goal synchronisation and ownership-checked workout deletion

The XML, Kotlin, PHP and SQL source should be submitted with the written report.
