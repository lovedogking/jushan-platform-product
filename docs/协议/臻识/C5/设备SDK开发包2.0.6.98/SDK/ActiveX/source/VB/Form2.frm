VERSION 5.00
Object = "{FC6B26EB-1C9A-46AA-BB44-503DD2948078}#1.0#0"; "VZLPRC~1.OCX"
Object = "{5E9E78A0-531B-11CF-91F6-C2863C385E30}#1.0#0"; "MSFLXGRD.OCX"
Begin VB.Form Form2 
   Caption         =   "Form2"
   ClientHeight    =   6075
   ClientLeft      =   120
   ClientTop       =   450
   ClientWidth     =   7995
   LinkTopic       =   "Form2"
   ScaleHeight     =   6075
   ScaleWidth      =   7995
   StartUpPosition =   3  '窗口缺省
   Begin VB.CommandButton Command5 
      Caption         =   "清空白名单"
      Height          =   375
      Left            =   240
      TabIndex        =   13
      Top             =   600
      Width           =   1215
   End
   Begin VB.CommandButton Command4 
      Caption         =   "删除"
      Height          =   375
      Left            =   360
      TabIndex        =   12
      Top             =   5640
      Width           =   855
   End
   Begin VB.CommandButton Command3 
      Caption         =   "编辑"
      Height          =   420
      Left            =   6720
      TabIndex        =   11
      Top             =   5640
      Width           =   975
   End
   Begin VB.CommandButton Command2 
      Caption         =   "添加"
      Height          =   375
      Left            =   5520
      TabIndex        =   10
      Top             =   5640
      Width           =   975
   End
   Begin VB.CheckBox Check2 
      Caption         =   "是否报警"
      Height          =   255
      Left            =   6480
      TabIndex        =   9
      Top             =   5160
      Width           =   1215
   End
   Begin VB.TextBox Text3 
      Height          =   270
      Left            =   4800
      TabIndex        =   8
      Text            =   "Text3"
      Top             =   5160
      Width           =   1575
   End
   Begin VB.CheckBox Check1 
      Caption         =   "是否启用"
      Height          =   255
      Left            =   2520
      TabIndex        =   6
      Top             =   5160
      Width           =   1095
   End
   Begin VB.TextBox Text2 
      Height          =   270
      Left            =   1080
      TabIndex        =   5
      Top             =   5160
      Width           =   1335
   End
   Begin VB.TextBox Text1 
      Height          =   375
      Left            =   5520
      TabIndex        =   3
      Top             =   600
      Width           =   1095
   End
   Begin VB.CommandButton Command1 
      Caption         =   "查询"
      Height          =   375
      Left            =   6840
      TabIndex        =   2
      Top             =   600
      Width           =   855
   End
   Begin VZLPRClientCtrlLib.VZLPRClientCtrl VZLPRClientCtrl1 
      Height          =   375
      Left            =   240
      TabIndex        =   1
      Top             =   120
      Visible         =   0   'False
      Width           =   1215
      _Version        =   65536
      _ExtentX        =   2143
      _ExtentY        =   661
      _StockProps     =   0
   End
   Begin MSFlexGridLib.MSFlexGrid MSFlexGrid1 
      Height          =   3975
      Left            =   240
      TabIndex        =   0
      Top             =   1080
      Width           =   7575
      _ExtentX        =   13361
      _ExtentY        =   7011
      _Version        =   393216
   End
   Begin VB.Label Label2 
      Caption         =   "过期时间"
      Height          =   255
      Left            =   3720
      TabIndex        =   7
      Top             =   5160
      Width           =   975
   End
   Begin VB.Line Line1 
      BorderColor     =   &H80000000&
      X1              =   360
      X2              =   7680
      Y1              =   5520
      Y2              =   5520
   End
   Begin VB.Label Label1 
      Caption         =   "车牌号"
      Height          =   255
      Left            =   360
      TabIndex        =   4
      Top             =   5160
      Width           =   615
   End
End
Attribute VB_Name = "Form2"
Attribute VB_GlobalNameSpace = False
Attribute VB_Creatable = False
Attribute VB_PredeclaredId = True
Attribute VB_Exposed = False
Public lprHandle As Long
Public rowIndex As Long

Private Sub Command1_Click()
  rowIndex = 0
  
  ' 删除之前的记录
  Dim rowCount As Long
  rowCount = MSFlexGrid1.Rows
  
  Do While rowCount > 2
    MSFlexGrid1.RemoveItem (rowCount)
    rowCount = rowCount - 1
  Loop
  
  Dim plateNum As String
  plateNum = Text1.Text
  
  
  Dim ret As Long
  ret = VZLPRClientCtrl1.VzLPRClientQueryWlistByPlate(plateNum, lprHandle)
  
 
End Sub

Private Sub Command2_Click()
 ' 添加白名单
 Dim ret As Long
 ret = VZLPRClientCtrl1.VzLPRClientAddWlist(Text2.Text, -1, Check1.Value, Text3.Text, False, Check2.Value, "", lprHandle)
 
 Command1_Click
 
End Sub

Private Sub Command3_Click()
' 编辑白名单
  Dim curRowIndex As Long
  curRowIndex = MSFlexGrid1.Row
 
  If curRowIndex > 0 Then
    Dim plateID As Long
    plateID = MSFlexGrid1.TextMatrix(curRowIndex, 0)
    
    Dim ret As Long
    ret = VZLPRClientCtrl1.VzLPRClientUpdateWlist(Text2.Text, -1, Check1.Value, Text3.Text, False, Check2.Value, "", plateID, lprHandle)
  End If
  
  Command1_Click
  
End Sub

Private Sub Command4_Click()
 Dim curRowIndex As Long
 curRowIndex = MSFlexGrid1.Row
 
 If curRowIndex > 0 Then
  Dim ret As Long
  ret = VZLPRClientCtrl1.VzLPRClientDeleteWlist(MSFlexGrid1.TextMatrix(curRowIndex, 1), lprHandle)
 End If
 
 Command1_Click
End Sub

Private Sub Command5_Click()
Dim ret As Long
ret = VZLPRClientCtrl1.VzLPRWhiteListClear(lprHandle)

Command1_Click
End Sub

Private Sub Form_Load()
 MSFlexGrid1.Cols = 5
 MSFlexGrid1.Rows = 2
 rowIndex = 0
 
 MSFlexGrid1.TextMatrix(0, 0) = "车牌ID"
 MSFlexGrid1.TextMatrix(0, 1) = "车牌号"
 MSFlexGrid1.TextMatrix(0, 2) = "是否启用"
 MSFlexGrid1.TextMatrix(0, 3) = "过期时间"
 MSFlexGrid1.TextMatrix(0, 4) = "是否报警"
 
 MSFlexGrid1.ColWidth(0) = 1200
 MSFlexGrid1.ColWidth(1) = 1600
 MSFlexGrid1.ColWidth(2) = 1200
 MSFlexGrid1.ColWidth(3) = 2200
 MSFlexGrid1.ColWidth(4) = 1200
 
 lprHandle = VZLPRClientCtrl1.VzLPRClientOpen("192.168.1.81", 80, "admin", "admin")
End Sub


Private Sub MSFlexGrid1_SelChange()
 ' 选中改变的事件
  Dim curRowIndex As Long
  curRowIndex = MSFlexGrid1.Row
 
  If curRowIndex > 0 Then
   Text2.Text = MSFlexGrid1.TextMatrix(curRowIndex, 1)
   If MSFlexGrid1.TextMatrix(curRowIndex, 2) = "True" Then
    Check1.Value = 1
   Else
    Check1.Value = 0
   End If
   
   Text3.Text = MSFlexGrid1.TextMatrix(curRowIndex, 3)
   
  If MSFlexGrid1.TextMatrix(curRowIndex, 4) = "True" Then
    Check2.Value = 1
   Else
    Check2.Value = 0
   End If
  End If
End Sub

Private Sub VZLPRClientCtrl1_OnLPRWlistInfoOut(ByVal lVehicleID As Long, ByVal strPlateID As String, ByVal bEnable As Boolean, ByVal strOverdule As String, ByVal bUsingTimeSeg As Boolean, ByVal bAlarm As Boolean, ByVal strPlateCode As String, ByVal lCustomerID As Long, ByVal strCustomerName As String, ByVal strCustomerCode As String)
 If rowIndex = 0 Then
  MSFlexGrid1.TextMatrix(1, 0) = lVehicleID
 Else
  MSFlexGrid1.AddItem (lVehicleID)
 End If
  
  rowIndex = rowIndex + 1
  MSFlexGrid1.TextMatrix(rowIndex, 1) = strPlateID
  MSFlexGrid1.TextMatrix(rowIndex, 2) = bEnable
  MSFlexGrid1.TextMatrix(rowIndex, 3) = strOverdule
  MSFlexGrid1.TextMatrix(rowIndex, 4) = bAlarm
  
End Sub
