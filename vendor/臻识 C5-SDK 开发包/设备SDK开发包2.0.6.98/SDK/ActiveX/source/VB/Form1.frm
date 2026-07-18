VERSION 5.00
Object = "{FC6B26EB-1C9A-46AA-BB44-503DD2948078}#1.0#0"; "VZLPRC~1.OCX"
Begin VB.Form Form1 
   Caption         =   "Form1"
   ClientHeight    =   6750
   ClientLeft      =   60
   ClientTop       =   450
   ClientWidth     =   10695
   LinkTopic       =   "Form1"
   ScaleHeight     =   6750
   ScaleWidth      =   10695
   StartUpPosition =   3  '窗口缺省
   Begin VB.CommandButton Command15 
      Caption         =   "485发送"
      Height          =   375
      Left            =   7560
      TabIndex        =   18
      Top             =   2760
      Width           =   855
   End
   Begin VZLPRClientCtrlLib.VZLPRClientCtrl VZLPRClientCtrl2 
      Height          =   2895
      Left            =   240
      TabIndex        =   17
      Top             =   3480
      Width           =   6615
      _Version        =   65536
      _ExtentX        =   11668
      _ExtentY        =   5106
      _StockProps     =   0
   End
   Begin VZLPRClientCtrlLib.VZLPRClientCtrl VZLPRClientCtrl1 
      Height          =   3135
      Left            =   120
      TabIndex        =   16
      Top             =   120
      Width           =   6855
      _Version        =   65536
      _ExtentX        =   12091
      _ExtentY        =   5530
      _StockProps     =   0
   End
   Begin VB.CommandButton Command14 
      Caption         =   "停止搜索"
      Height          =   255
      Left            =   9120
      TabIndex        =   0
      Top             =   2400
      Width           =   1095
   End
   Begin VB.CommandButton Command13 
      Caption         =   "开始搜索"
      Height          =   255
      Left            =   7320
      TabIndex        =   6
      Top             =   2400
      Width           =   975
   End
   Begin VB.CommandButton Command12 
      Caption         =   "继电器"
      Height          =   255
      Left            =   9120
      TabIndex        =   15
      Top             =   2040
      Width           =   975
   End
   Begin VB.CommandButton Command11 
      Caption         =   "GPIO"
      Height          =   255
      Left            =   7440
      TabIndex        =   14
      Top             =   2040
      Width           =   975
   End
   Begin VB.CommandButton Command10 
      Caption         =   "白名单"
      Height          =   495
      Left            =   9000
      TabIndex        =   13
      Top             =   1440
      Width           =   1095
   End
   Begin VB.CommandButton Command9 
      Caption         =   "抓图"
      Height          =   495
      Left            =   7200
      TabIndex        =   12
      Top             =   1320
      Width           =   1215
   End
   Begin VB.CommandButton Command8 
      Caption         =   "打开"
      Height          =   495
      Left            =   7080
      TabIndex        =   11
      Top             =   4200
      Width           =   1215
   End
   Begin VB.CommandButton Command7 
      Caption         =   "关闭"
      Height          =   495
      Left            =   8880
      TabIndex        =   10
      Top             =   4200
      Width           =   1215
   End
   Begin VB.CommandButton Command6 
      Caption         =   "播放"
      Height          =   495
      Left            =   7080
      TabIndex        =   9
      Top             =   4800
      Width           =   1215
   End
   Begin VB.CommandButton Command5 
      Caption         =   "停止"
      Height          =   495
      Left            =   8880
      TabIndex        =   8
      Top             =   4800
      Width           =   1215
   End
   Begin VB.ListBox List2 
      Height          =   960
      Left            =   7080
      TabIndex        =   7
      Top             =   5400
      Width           =   3015
   End
   Begin VB.ListBox List1 
      Height          =   780
      Left            =   7200
      TabIndex        =   5
      Top             =   3360
      Width           =   2895
   End
   Begin VB.CommandButton Command4 
      Caption         =   "停止"
      Height          =   495
      Left            =   9000
      TabIndex        =   4
      Top             =   840
      Width           =   1215
   End
   Begin VB.CommandButton Command3 
      Caption         =   "播放"
      Height          =   495
      Left            =   7200
      TabIndex        =   3
      Top             =   840
      Width           =   1215
   End
   Begin VB.CommandButton Command2 
      Caption         =   "关闭"
      Height          =   495
      Left            =   9000
      TabIndex        =   2
      Top             =   240
      Width           =   1215
   End
   Begin VB.CommandButton Command1 
      Caption         =   "打开"
      Height          =   495
      Left            =   7200
      TabIndex        =   1
      Top             =   240
      Width           =   1215
   End
End
Attribute VB_Name = "Form1"
Attribute VB_GlobalNameSpace = False
Attribute VB_Creatable = False
Attribute VB_PredeclaredId = True
Attribute VB_Exposed = False
Public lprHandle As Long
Public lprHandle2 As Long

Private Sub Command1_Click()
lprHandle = VZLPRClientCtrl1.VzLPRClientOpen("192.168.1.81", 80, "admin", "admin")
VZLPRClientCtrl1.SetIsSaveFullImage lprHandle, True, "D:\\lprImg"

Dim version As String
version = VZLPRClientCtrl1.VzLPRGetVersion()

Dim ret As Long
ret = VZLPRClientCtrl1.VzLPRSetIsSavePlateImage(lprHandle, True, "D:\\lprImg")


End Sub

Private Sub Command10_Click()
' Dim ret As Long
' ret = VZLPRClientCtrl1.VzLPRClientAddWlist("川A65432", -1, True, "2015-04-06 12:30:30", False, False, "", lprHandle)
Form2.Show vbModal, Me

End Sub

Private Sub Command11_Click()
Dim gpio As Long

If lprHandle > 0 Then
gpio = VZLPRClientCtrl1.VzLPRGetGPIOValue(lprHandle, 0)
End If

End Sub

Private Sub Command12_Click()
 Dim ret As Long
If lprHandle > 0 Then
ret = VZLPRClientCtrl1.VzLPRSetIOOutputAuto(lprHandle, 0, 500)
End If

End Sub

Private Sub Command13_Click()
Dim ret As Long
ret = VZLPRClientCtrl1.VzLPRStartFindDevice()
ret = VZLPRClientCtrl1.VzLPRSetOsdParam(lprHandle, "test")
End Sub

Private Sub Command14_Click()
Dim ret As Long
ret = VZLPRClientCtrl1.VzLPRStopFindDevice
End Sub

Private Sub Command15_Click()
 Dim ret As Long
 Dim serialHandle As Long
 
 'ret = VZLPRClientCtrl1.VzLPRSetSerialParameter(lprHandle, 0, 19200, 0, 8, 1)
 
 If lprHandle > 0 Then
 
    serialHandle = VZLPRClientCtrl1.VzLPRSerialStartEx(lprHandle, 0)
    If serialHandle > 0 Then
      ret = VZLPRClientCtrl1.VzLPRSerialSendEx(lprHandle, serialHandle, "ABCCDF2CED", 5)
    End If
    
 End If

'ret = VZLPRClientCtrl1.VzLPRSerialStop(0)
End Sub

Private Sub Command2_Click()

If lprHandle > 0 Then
VZLPRClientCtrl1.VzLPRClientClose (lprHandle)
lprHandle = 0
End If
End Sub

Private Sub Command3_Click()
 Dim ret As Long
If lprHandle > 0 Then
ret = VZLPRClientCtrl1.VzLPRClientStartPlay(lprHandle, 0)
End If
End Sub

Private Sub Command4_Click()
Dim ret As Boolean
If lprHandle > 0 Then
ret = VZLPRClientCtrl1.VzLPRClientStopPlay(0)
End If
End Sub

Private Sub Command5_Click()
Dim ret As Boolean
If lprHandle2 > 0 Then
ret = VZLPRClientCtrl2.VzLPRClientStopPlay(0)
End If
End Sub

Private Sub Command6_Click()
 Dim ret As Long
If lprHandle2 > 0 Then
ret = VZLPRClientCtrl2.VzLPRClientStartPlay(lprHandle2, 0)
End If
End Sub

Private Sub Command7_Click()
    If lprHandle2 > 0 Then
    VZLPRClientCtrl2.VzLPRClientClose (lprHandle2)
    lprHandle2 = 0
    End If
End Sub

Private Sub Command8_Click()
    lprHandle2 = VZLPRClientCtrl2.VzLPRClientOpen("192.168.1.83", 80, "admin", "admin")
End Sub

Private Sub Command9_Click()
Dim ret As Boolean
ret = VZLPRClientCtrl1.VzLPRClientCaptureImg(0, "D:\\test_lpr.jpg")
End Sub

Private Sub Form_Load()
    VZLPRClientCtrl1.SetWindowStyle (1)
    VZLPRClientCtrl2.SetWindowStyle (1)
End Sub

Private Sub VZLPRClientCtrl1_OnLPRFindDeviceInfoOut(ByVal szIPAddr As String, ByVal usPort1 As Long, ByVal usPort2 As Long, ByVal SL As Long, ByVal SH As Long)
List1.AddItem szIPAddr
End Sub

'Private Sub VZLPRClientCtrl1_OnLPRPlateInfoOut(ByVal license As String, ByVal color As String, ByVal colorIndex As Integer, ByVal nType As Integer, ByVal confidence As Integer, ByVal bright As Integer, ByVal nDirection As Integer, ByVal time As Long, ByVal carBright As Integer, ByVal carColor As Integer, ByVal ip As String, ByVal resultType As Integer)
'List1.AddItem license
'End Sub

'Private Sub VZLPRClientCtrl2_OnLPRPlateInfoOut(ByVal license As String, ByVal color As String, ByVal colorIndex As Integer, ByVal nType As Integer, ByVal confidence As Integer, ByVal bright As Integer, ByVal nDirection As Integer, ByVal time As Long, ByVal carBright As Integer, ByVal carColor As Integer, ByVal ip As String, ByVal resultType As Integer)
'List2.AddItem license
'End Sub
Private Sub VZLPRClientCtrl1_OnLPRPlateInfoOut(ByVal license As String, ByVal color As String, ByVal colorIndex As Integer, ByVal nType As Integer, ByVal confidence As Integer, ByVal bright As Integer, ByVal nDirection As Integer, ByVal time As Long, ByVal carBright As Integer, ByVal carColor As Integer, ByVal ip As String, ByVal resultType As Integer)
'List1.AddItem license
End Sub



Private Sub VZLPRClientCtrl1_OnLPRPlateInfoOutEx2(ByVal license As String, ByVal color As String, ByVal colorIndex As Integer, ByVal nType As Integer, ByVal confidence As Integer, ByVal bright As Integer, ByVal nDirection As Integer, ByVal time As Long, ByVal carBright As Integer, ByVal carColor As Integer, ByVal imgPath As String, ByVal plateLeft As Integer, ByVal plateTop As Integer, ByVal plateRight As Integer, ByVal plateBottom As Integer, ByVal imgPlatePath As String, ByVal plateTime As String, ByVal ip As String, ByVal resultType As Integer)
List1.AddItem license
End Sub

Private Sub VZLPRClientCtrl1_OnLPRSerialRecvData(ByVal lSerialHandle As Long, ByVal recvData As String, ByVal ip As String, ByVal lHandle As Long)
 Dim ret As Long
 ret = 0
End Sub

Private Sub VZLPRClientCtrl2_OnLPRPlateInfoOut(ByVal license As String, ByVal color As String, ByVal colorIndex As Integer, ByVal nType As Integer, ByVal confidence As Integer, ByVal bright As Integer, ByVal nDirection As Integer, ByVal time As Long, ByVal carBright As Integer, ByVal carColor As Integer, ByVal ip As String, ByVal resultType As Integer)
List2.AddItem license
End Sub

Private Sub VZLPRClientCtrl2_OnLPRPlateInfoOutEx2(ByVal license As String, ByVal color As String, ByVal colorIndex As Integer, ByVal nType As Integer, ByVal confidence As Integer, ByVal bright As Integer, ByVal nDirection As Integer, ByVal time As Long, ByVal carBright As Integer, ByVal carColor As Integer, ByVal imgPath As String, ByVal plateLeft As Integer, ByVal plateTop As Integer, ByVal plateRight As Integer, ByVal plateBottom As Integer, ByVal imgPlatePath As String, ByVal plateTime As String, ByVal ip As String, ByVal resultType As Integer)
 List1.AddItem license
End Sub
