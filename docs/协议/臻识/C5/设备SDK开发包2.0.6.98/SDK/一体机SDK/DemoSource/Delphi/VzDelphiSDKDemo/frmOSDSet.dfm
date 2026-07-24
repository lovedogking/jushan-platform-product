object FormOSDSet: TFormOSDSet
  Left = 1156
  Top = 389
  Width = 403
  Height = 485
  Caption = 'OSD'#37197#32622
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
  object lblDayStyle: TLabel
    Left = 128
    Top = 48
    Width = 65
    Height = 17
    AutoSize = False
    Caption = #26085#26399#26684#24335':'
    WordWrap = True
  end
  object Label1: TLabel
    Left = 128
    Top = 88
    Width = 41
    Height = 13
    AutoSize = False
    Caption = #22352#26631':'
    WordWrap = True
  end
  object lbl1: TLabel
    Left = 208
    Top = 80
    Width = 25
    Height = 17
    AutoSize = False
    Caption = 'X'#65306
    WordWrap = True
  end
  object lbl2: TLabel
    Left = 288
    Top = 80
    Width = 17
    Height = 13
    AutoSize = False
    Caption = 'Y:'
    WordWrap = True
  end
  object lbl3: TLabel
    Left = 128
    Top = 128
    Width = 65
    Height = 17
    AutoSize = False
    Caption = #26102#38388#26684#24335':'
    WordWrap = True
  end
  object lbl4: TLabel
    Left = 128
    Top = 160
    Width = 41
    Height = 13
    AutoSize = False
    Caption = #22352#26631':'
    WordWrap = True
  end
  object lbl5: TLabel
    Left = 208
    Top = 160
    Width = 25
    Height = 17
    AutoSize = False
    Caption = 'X'#65306
    WordWrap = True
  end
  object lbl6: TLabel
    Left = 288
    Top = 160
    Width = 17
    Height = 13
    AutoSize = False
    Caption = 'Y:'
    WordWrap = True
  end
  object lbl7: TLabel
    Left = 128
    Top = 208
    Width = 65
    Height = 17
    AutoSize = False
    Caption = #26102#38388#26684#24335':'
    WordWrap = True
  end
  object lbl8: TLabel
    Left = 128
    Top = 240
    Width = 41
    Height = 13
    AutoSize = False
    Caption = #22352#26631':'
    WordWrap = True
  end
  object lbl9: TLabel
    Left = 208
    Top = 240
    Width = 25
    Height = 17
    AutoSize = False
    Caption = 'X'#65306
    WordWrap = True
  end
  object lbl10: TLabel
    Left = 288
    Top = 240
    Width = 17
    Height = 13
    AutoSize = False
    Caption = 'Y:'
    WordWrap = True
  end
  object chkOSDDay: TCheckBox
    Left = 40
    Top = 48
    Width = 57
    Height = 17
    Caption = #26085#26399
    TabOrder = 0
  end
  object cbbDayStyle: TComboBox
    Left = 208
    Top = 40
    Width = 137
    Height = 21
    ItemHeight = 13
    TabOrder = 1
  end
  object edtOSDDayX: TEdit
    Left = 232
    Top = 80
    Width = 41
    Height = 21
    TabOrder = 2
  end
  object edtOSDDayY: TEdit
    Left = 304
    Top = 80
    Width = 41
    Height = 21
    TabOrder = 3
  end
  object chkOSDTime: TCheckBox
    Left = 40
    Top = 128
    Width = 57
    Height = 17
    Caption = #26102#38388
    TabOrder = 4
  end
  object cbbOSDTime: TComboBox
    Left = 208
    Top = 128
    Width = 137
    Height = 21
    ItemHeight = 13
    TabOrder = 5
  end
  object edtOSDTimeX: TEdit
    Left = 232
    Top = 160
    Width = 41
    Height = 21
    TabOrder = 6
  end
  object edtOSDTimeY: TEdit
    Left = 304
    Top = 160
    Width = 41
    Height = 21
    TabOrder = 7
  end
  object chkOSDText: TCheckBox
    Left = 40
    Top = 208
    Width = 57
    Height = 17
    Caption = #25991#23383
    TabOrder = 8
  end
  object edtOSDText: TEdit
    Left = 208
    Top = 208
    Width = 137
    Height = 21
    TabOrder = 9
  end
  object edtOSDTextX: TEdit
    Left = 232
    Top = 240
    Width = 41
    Height = 21
    TabOrder = 10
  end
  object edtOSDTextY: TEdit
    Left = 304
    Top = 240
    Width = 41
    Height = 21
    TabOrder = 11
  end
  object chkUpdateTime: TCheckBox
    Left = 40
    Top = 288
    Width = 105
    Height = 17
    Caption = #26159#21542#26356#26032#26102#38388
    TabOrder = 12
  end
  object btnOSDSave: TButton
    Left = 104
    Top = 384
    Width = 73
    Height = 25
    Caption = #20445#23384
    TabOrder = 13
    OnClick = btnOSDSaveClick
  end
  object btnOSDCancel: TButton
    Left = 224
    Top = 384
    Width = 73
    Height = 25
    Caption = #21462#28040
    TabOrder = 14
    OnClick = btnOSDCancelClick
  end
  object dtpDate: TDateTimePicker
    Left = 88
    Top = 328
    Width = 97
    Height = 21
    Date = 42634.674016817130000000
    Time = 42634.674016817130000000
    TabOrder = 15
  end
  object dtpTime: TDateTimePicker
    Left = 208
    Top = 328
    Width = 97
    Height = 21
    Date = 42634.674595613430000000
    Time = 42634.674595613430000000
    Kind = dtkTime
    TabOrder = 16
  end
end
