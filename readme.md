
'''
keytool -list -v \
-alias androiddebugkey -keystore ~/.android/debug.keystore
'''

'''
keytool -printcert -jarfile app/build/outputs/apk/debug/app-debug.apk
keytool -list -v -keystore your-release-key.keystore
'''

'''
./gradlew signingReport
'''

'''
./gradlew installDebug
'''