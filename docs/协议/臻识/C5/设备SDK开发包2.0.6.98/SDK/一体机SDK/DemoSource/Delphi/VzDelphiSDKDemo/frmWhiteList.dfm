object FormWhiteList: TFormWhiteList
  Left = 500
  Top = 93
  Width = 530
  Height = 388
  Caption = #30333#21517#21333
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
  object btnWLAdd: TButton
    Left = 16
    Top = 32
    Width = 65
    Height = 25
    Caption = #28155#21152
    TabOrder = 0
    OnClick = btnWLAddClick
  end
  object btnWLChange: TButton
    Left = 104
    Top = 32
    Width = 65
    Height = 25
    Caption = #20462#25913
    TabOrder = 1
    OnClick = btnWLChangeClick
  end
  object btnWLDelete: TButton
    Left = 192
    Top = 32
    Width = 65
    Height = 25
    Caption = #21024#38500
    TabOrder = 2
    OnClick = btnWLDeleteClick
  end
  object edtWLPlate: TEdit
    Left = 320
    Top = 32
    Width = 97
    Height = 21
    TabOrder = 3
  end
  object btnWLQuery: TButton
    Left = 432
    Top = 32
    Width = 65
    Height = 25
    Caption = #26597#35810
    TabOrder = 4
    OnClick = btnWLQueryClick
  end
  object lvWL: TListView
    Left = 16
    Top = 72
    Width = 481
    Height = 257
    Columns = <>
    GridLines = True
    TabOrder = 5
    ViewStyle = vsReport
  end
end
