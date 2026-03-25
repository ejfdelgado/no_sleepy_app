
'''
keytool -list -v \
-alias androiddebugkey -keystore ~/.android/debug.keystore
'''

'''
keytool -printcert -jarfile app/build/outputs/apk/debug/app-debug.apk
'''

'''
./gradlew signingReport
'''

'''
./gradlew installDebug
'''