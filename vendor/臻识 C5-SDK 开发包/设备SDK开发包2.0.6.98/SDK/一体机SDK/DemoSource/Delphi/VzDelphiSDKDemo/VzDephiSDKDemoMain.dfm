object FormMain: TFormMain
  Left = 333
  Top = 287
  Width = 1050
  Height = 669
  Caption = #36710#29260#35782#21035#19968#20307#26426'Delphi'#31034#20363
  Color = clWhite
  Font.Charset = DEFAULT_CHARSET
  Font.Color = clWindowText
  Font.Height = -11
  Font.Name = 'MS Sans Serif'
  Font.Style = []
  OldCreateOrder = False
  Position = poScreenCenter
  OnClose = FromClose
  OnCreate = FromCreate
  PixelsPerInch = 96
  TextHeight = 13
  object lblPlateResult1: TLabel
    Left = 0
    Top = 304
    Width = 89
    Height = 13
    AutoSize = False
    Caption = #36710#29260#35782#21035#32467#26524
    WordWrap = True
  end
  object lblPlateResult2: TLabel
    Left = 360
    Top = 304
    Width = 89
    Height = 13
    AutoSize = False
    Caption = #36710#29260#35782#21035#32467#26524
    WordWrap = True
  end
  object imgPlate1: TImage
    Left = 0
    Top = 328
    Width = 353
    Height = 297
    Stretch = True
    Transparent = True
  end
  object imgPlate2: TImage
    Left = 360
    Top = 328
    Width = 353
    Height = 297
    Stretch = True
  end
  object GroupBoxLogin: TGroupBox
    Left = 728
    Top = 136
    Width = 281
    Height = 153
    Caption = #30331#24405
    TabOrder = 0
    object lblDevIP: TLabel
      Left = 8
      Top = 24
      Width = 41
      Height = 13
      AutoSize = False
      Caption = #35774#22791'IP'
      WordWrap = True
    end
    object lblUsername: TLabel
      Left = 8
      Top = 56
      Width = 49
      Height = 13
      AutoSize = False
      Caption = #29992#25143#21517
      WordWrap = True
    end
    object lblPort: TLabel
      Left = 168
      Top = 24
      Width = 27
      Height = 13
      Caption = #31471#21475':'
    end
    object lblPwd: TLabel
      Left = 8
      Top = 88
      Width = 57
      Height = 13
      AutoSize = False
      Caption = #30331#24405#23494#30721
    end
    object lblUserPwd: TLabel
      Left = 144
      Top = 88
      Width = 65
      Height = 13
      AutoSize = False
      Caption = #29992#25143#23494#30721
    end
    object edtDevIP: TEdit
      Left = 72
      Top = 24
      Width = 81
      Height = 21
      TabOrder = 0
      Text = '192.168.7.21'
    end
    object edtPort: TEdit
      Left = 208
      Top = 24
      Width = 49
      Height = 21
      TabOrder = 1
      Text = '80'
    end
    object edtUsername: TEdit
      Left = 72
      Top = 56
      Width = 65
      Height = 21
      TabOrder = 2
      Text = 'admin'
    end
    object edtPwd: TEdit
      Left = 72
      Top = 88
      Width = 65
      Height = 20
      Font.Charset = SYMBOL_CHARSET
      Font.Color = clWindowText
      Font.Height = -11
      Font.Name = 'Wingdings'
      Font.Style = []
      ParentFont = False
      PasswordChar = 'l'
      TabOrder = 3
      Text = 'admin'
    end
    object btnOpen: TButton
      Left = 64
      Top = 120
      Width = 57
      Height = 25
      Caption = #25171#24320
      TabOrder = 4
      OnClick = btnOpenClick
    end
    object btnClose: TButton
      Left = 176
      Top = 120
      Width = 57
      Height = 25
      Caption = #20851#38381
      TabOrder = 5
      OnClick = btnCloseClick
    end
    object edtUserPwd: TEdit
      Left = 208
      Top = 88
      Width = 65
      Height = 20
      Font.Charset = SYMBOL_CHARSET
      Font.Color = clWindowText
      Font.Height = -11
      Font.Name = 'Wingdings'
      Font.Style = []
      ParentFont = False
      PasswordChar = 'l'
      TabOrder = 6
      Text = '123456789'
    end
  end
  object pnlVideo1: TPanel
    Left = 0
    Top = 0
    Width = 353
    Height = 289
    Color = clSilver
    TabOrder = 1
    OnClick = video1Play
  end
  object pnlVideo2: TPanel
    Left = 360
    Top = 0
    Width = 353
    Height = 289
    Color = clSilver
    TabOrder = 2
    OnClick = video2Play
  end
  object btnVideoPlay: TButton
    Left = 728
    Top = 408
    Width = 65
    Height = 25
    Caption = #25773#25918
    TabOrder = 3
    OnClick = btnVideoPlayClick
  end
  object btnVideoStop: TButton
    Left = 800
    Top = 408
    Width = 65
    Height = 25
    Caption = #20572#27490
    TabOrder = 4
    OnClick = btnVideoStopClick
  end
  object btnManual: TButton
    Left = 728
    Top = 376
    Width = 65
    Height = 25
    Caption = #25163#21160#35782#21035
    TabOrder = 5
    OnClick = btnManualClick
  end
  object edtplate1: TEdit
    Left = 88
    Top = 296
    Width = 89
    Height = 21
    TabOrder = 6
  end
  object edtPlate2: TEdit
    Left = 448
    Top = 296
    Width = 89
    Height = 21
    TabOrder = 7
  end
  object btnRecord: TButton
    Left = 872
    Top = 408
    Width = 65
    Height = 25
    Caption = #24405#20687
    TabOrder = 8
    OnClick = btnRecordClick
  end
  object btnRecordStop: TButton
    Left = 944
    Top = 408
    Width = 65
    Height = 25
    Caption = #20572#27490#24405#20687
    TabOrder = 9
    OnClick = btnRecordStopClick
  end
  object btnCapture: TButton
    Left = 800
    Top = 376
    Width = 65
    Height = 25
    Caption = #25235#22270
    TabOrder = 10
    OnClick = btnCaptureClick
  end
  object btnGPIO: TButton
    Left = 872
    Top = 376
    Width = 65
    Height = 25
    Caption = 'GPIO'
    TabOrder = 11
    OnClick = btnGPIOClick
  end
  object btnIsConnected: TButton
    Left = 944
    Top = 376
    Width = 65
    Height = 25
    Caption = #36830#25509#29366#24577
    TabOrder = 12
    OnClick = btnIsConnectedClick
  end
  object btnOSDCfg: TButton
    Left = 728
    Top = 472
    Width = 65
    Height = 25
    Caption = 'OSD'#37197#32622
    TabOrder = 13
    OnClick = btnOSDCfgClick
  end
  object btnBaseConfig: TButton
    Left = 872
    Top = 472
    Width = 65
    Height = 25
    Caption = #22522#26412#37197#32622
    TabOrder = 14
    OnClick = btnBaseConfigClick
  end
  object btnVideoConfig: TButton
    Left = 800
    Top = 472
    Width = 65
    Height = 25
    Caption = #35270#39057#37197#32622
    TabOrder = 15
    OnClick = btnVideoConfigClick
  end
  object btnInAndOut: TButton
    Left = 944
    Top = 440
    Width = 65
    Height = 25
    Caption = #36755#20837#36755#20986
    TabOrder = 16
    OnClick = btnInAndOutClick
  end
  object grp1: TGroupBox
    Left = 728
    Top = 8
    Width = 281
    Height = 121
    Caption = #26597#25214#35774#22791
    TabOrder = 17
    object btnStartSearchDev: TButton
      Left = 8
      Top = 24
      Width = 57
      Height = 25
      Caption = #24320#22987
      TabOrder = 0
      OnClick = btnStartSearchDevClick
    end
    object btnStopSearchDev: TButton
      Left = 8
      Top = 56
      Width = 57
      Height = 25
      Caption = #20572#27490
      TabOrder = 1
      OnClick = btnStopSearchDevClick
    end
    object btnNetCfg: TButton
      Left = 8
      Top = 88
      Width = 57
      Height = 25
      Caption = #20462#25913'IP'
      TabOrder = 2
      OnClick = btnNetCfgClick
    end
    object tvFindDev: TTreeView
      Left = 72
      Top = 16
      Width = 201
      Height = 97
      Indent = 19
      TabOrder = 3
    end
  end
  object btnWhiteList: TButton
    Left = 728
    Top = 440
    Width = 65
    Height = 25
    Caption = #30333#21517#21333
    TabOrder = 18
    OnClick = btnWhiteListClick
  end
  object btnPlateQuery: TButton
    Left = 800
    Top = 440
    Width = 65
    Height = 25
    Caption = #36710#29260#26597#35810
    TabOrder = 19
    OnClick = btnPlateQueryClick
  end
  object btnRS485: TButton
    Left = 872
    Top = 440
    Width = 65
    Height = 25
    Caption = '485'#25511#21046
    TabOrder = 20
    OnClick = btnRS485Click
  end
  object tvOpenDevice: TTreeView
    Left = 728
    Top = 296
    Width = 281
    Height = 65
    Indent = 19
    TabOrder = 21
  end
  object btnPlayVoice: TButton
    Left = 944
    Top = 472
    Width = 65
    Height = 25
    Caption = #35821#38899#25773#25918
    TabOrder = 22
    OnClick = btnPlayVoiceClick
  end
end
