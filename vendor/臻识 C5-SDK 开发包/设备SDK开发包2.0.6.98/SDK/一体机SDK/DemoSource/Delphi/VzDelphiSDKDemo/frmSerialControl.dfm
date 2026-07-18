object FormSerialControl: TFormSerialControl
  Left = 869
  Top = 300
  Width = 504
  Height = 542
  Caption = '485'#25511#21046
  Color = clBtnFace
  Font.Charset = DEFAULT_CHARSET
  Font.Color = clWindowText
  Font.Height = -11
  Font.Name = 'MS Sans Serif'
  Font.Style = []
  OldCreateOrder = False
  Position = poScreenCenter
  PixelsPerInch = 96
  TextHeight = 13
  object lbl1: TLabel
    Left = 32
    Top = 48
    Width = 49
    Height = 13
    AutoSize = False
    Caption = #20018#21475#21495
    WordWrap = True
  end
  object cbbSerialPort: TComboBox
    Left = 104
    Top = 48
    Width = 113
    Height = 21
    ItemHeight = 13
    TabOrder = 0
  end
  object btnSerialStart: TButton
    Left = 280
    Top = 48
    Width = 65
    Height = 25
    Caption = #24320#22987
    TabOrder = 1
    OnClick = btnSerialStartClick
  end
  object btnSerialStop: TButton
    Left = 368
    Top = 48
    Width = 57
    Height = 25
    Caption = #20572#27490
    TabOrder = 2
    OnClick = btnSerialStopClick
  end
  object edtSerialRecv: TEdit
    Left = 32
    Top = 88
    Width = 425
    Height = 145
    AutoSize = False
    TabOrder = 3
  end
  object chkBoxHex: TCheckBox
    Left = 32
    Top = 256
    Width = 121
    Height = 17
    Caption = #21313#20845#36827#21046#26174#31034
    TabOrder = 4
  end
  object btnClean: TButton
    Left = 392
    Top = 248
    Width = 65
    Height = 25
    Caption = #28165#38500
    TabOrder = 5
    OnClick = btnCleanClick
  end
  object edtSerialSend: TEdit
    Left = 32
    Top = 296
    Width = 425
    Height = 145
    AutoSize = False
    TabOrder = 6
  end
  object chkSendHex: TCheckBox
    Left = 32
    Top = 464
    Width = 177
    Height = 17
    Caption = #21313#20845#36827#21046#21457#36865'( 01 FE ...)'
    TabOrder = 7
  end
  object btnSerialSend: TButton
    Left = 392
    Top = 464
    Width = 65
    Height = 25
    Caption = #21457#36865
    TabOrder = 8
    OnClick = btnSerialSendClick
  end
end
