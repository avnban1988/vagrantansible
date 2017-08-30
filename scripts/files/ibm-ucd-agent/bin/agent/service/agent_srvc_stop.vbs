' Licensed Materials - Property of IBM Corp.
' IBM UrbanCode Build
' IBM UrbanCode Deploy
' IBM UrbanCode Release
' IBM AnthillPro
' (c) Copyright IBM Corporation 2002, 2014. All Rights Reserved.
'
' U.S. Government Users Restricted Rights - Use, duplication or disclosure restricted by
' GSA ADP Schedule Contract with IBM Corp.
strServiceName = WScript.Arguments.Item(0)
strComputer = "."

'--- checking the given service

    WScript.Echo "Checking the service..." & vbCrLf

    Set objWMIService = GetObject("winmgmts:" _
        & "{impersonationLevel=impersonate}!\\" & strComputer & "\root\cimv2")
    Set colRunningServices = objWMIService.ExecQuery _
        ("Select * from Win32_Service where Name = '" & strServiceName & "'")

'--- stopping the given service

    If colRunningServices.Count > 0 Then
        For Each objCurrService in colRunningServices
            If objCurrService.Name = strServiceName Then
                If objCurrService.State = "Stopped" Then
                    WScript.Echo "Your service is already stopped."
                Else
                    intRC = objCurrService.StopService()
                    WScript.Sleep 5000
                    If (objCurrService.State = "Stopped" And intRC = 0) Then
                        WScript.Echo "Service '" & strServiceName + "' successfully stopped."
                    Else
                        WScript.Echo "Error '" & intRC & "' during service stopping."
                    
                    End If
                End If
            End If
        Next
    End If

