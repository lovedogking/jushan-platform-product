object FormVideoCfg: TFormVideoCfg
  Left = 964
  Top = 358
  Width = 409
  Height = 454
  Caption = #35270#39057#37197#32622
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
  object pgcVideoCfg: TPageControl
    Left = 8
    Top = 8
    Width = 377
    Height = 401
    ActivePage = tsVideoSource
    TabOrder = 0
    object tsMainDataRate: TTabSheet
      Caption = #20027#30721#27969
      object lbl1: TLabel
        Left = 48
        Top = 32
        Width = 49
        Height = 17
        AutoSize = False
        Caption = #20998#36776#29575
        WordWrap = True
      end
      object lbl2: TLabel
        Left = 48
        Top = 72
        Width = 41
        Height = 13
        AutoSize = False
        Caption = #24103#29575
        WordWrap = True
      end
      object lbl3: TLabel
        Left = 48
        Top = 112
        Width = 65
        Height = 13
        AutoSize = False
        Caption = #32534#30721#26041#24335
        WordWrap = True
      end
      object lbl4: TLabel
        Left = 48
        Top = 152
        Width = 65
        Height = 13
        AutoSize = False
        Caption = #30721#27969#25511#21046
        WordWrap = True
      end
      object lbl5: TLabel
        Left = 48
        Top = 192
        Width = 57
        Height = 13
        AutoSize = False
        Caption = #22270#29255#36136#37327
        WordWrap = True
      end
      object lbl6: TLabel
        Left = 48
        Top = 232
        Width = 65
        Height = 13
        AutoSize = False
        Caption = #30721#27969#19978#38480
        WordWrap = True
      end
      object cbbframe_size: TComboBox
        Left = 136
        Top = 32
        Width = 145
        Height = 21
        ItemHeight = 0
        TabOrder = 0
      end
      object cbbframe_rate: TComboBox
        Left = 136
        Top = 72
        Width = 145
        Height = 21
        ItemHeight = 0
        TabOrder = 1
      end
      object cbbencode_type: TComboBox
        Left = 136
        Top = 112
        Width = 145
        Height = 21
        ItemHeight = 0
        TabOrder = 2
        OnChange = cmb_encode_type_SelectedIndexChanged
      end
      object cbbcompress_mode: TComboBox
        Left = 136
        Top = 152
        Width = 145
        Height = 21
        ItemHeight = 0
        TabOrder = 3
        OnChange = cmb_compress_mode_SelectedIndexChanged
      end
      object cbbimg_quality: TComboBox
        Left = 136
        Top = 192
        Width = 145
        Height = 21
        ItemHeight = 0
        TabOrder = 4
      end
      object edtrateval: TEdit
        Left = 136
        Top = 232
        Width = 145
        Height = 21
        TabOrder = 5
      end
      object btnOK: TButton
        Left = 200
        Top = 288
        Width = 75
        Height = 25
        Caption = #30830#23450
        TabOrder = 6
        OnClick = btnOKClick
      end
    end
    object tsVideoSource: TTabSheet
      Caption = #35270#39057#28304
      ImageIndex = 1
      object lbl7: TLabel
        Left = 48
        Top = 24
        Width = 33
        Height = 13
        AutoSize = False
        Caption = #20142#24230
        WordWrap = True
      end
      object lbl8: TLabel
        Left = 48
        Top = 56
        Width = 49
        Height = 13
        AutoSize = False
        Caption = #23545#27604#24230
        WordWrap = True
      end
      object lbl9: TLabel
        Left = 48
        Top = 88
        Width = 57
        Height = 13
        AutoSize = False
        Caption = #39281#21644#24230
        WordWrap = True
      end
      object lbl10: TLabel
        Left = 48
        Top = 120
        Width = 57
        Height = 13
        AutoSize = False
        Caption = #28165#26224#24230
        WordWrap = True
      end
      object lbl11: TLabel
        Left = 48
        Top = 184
        Width = 57
        Height = 13
        AutoSize = False
        Caption = #35270#39057#21046#24335
        WordWrap = True
      end
      object lbl12: TLabel
        Left = 48
        Top = 224
        Width = 57
        Height = 13
        AutoSize = False
        Caption = #26333#20809#26102#38388
        WordWrap = True
      end
      object lbl13: TLabel
        Left = 48
        Top = 264
        Width = 57
        Height = 13
        AutoSize = False
        Caption = #22270#20687#32763#36716
        WordWrap = True
      end
      object cbbvideo_standard: TComboBox
        Left = 128
        Top = 184
        Width = 145
        Height = 21
        ItemHeight = 13
        TabOrder = 0
        OnChange = cbbvideo_standard_SelectedIndexChanged
      end
      object cbbexposure_time: TComboBox
        Left = 128
        Top = 224
        Width = 145
        Height = 21
        ItemHeight = 13
        TabOrder = 1
        OnChange = cbbexposure_time_SelectedIndexChanged
      end
      object cbbimg_pos: TComboBox
        Left = 128
        Top = 264
        Width = 145
        Height = 21
        ItemHeight = 13
        TabOrder = 2
        OnChange = cbbimg_pos_SelectedIndexChanged
      end
      object btnRecovery: TButton
        Left = 248
        Top = 320
        Width = 83
        Height = 25
        Caption = #24674#22797#40664#35748
        TabOrder = 3
        OnClick = btnRecoveryClick
      end
      object trckbrBright: TTrackBar
        Left = 112
        Top = 24
        Width = 166
        Height = 9
        Max = 100
        Frequency = 5
        TabOrder = 4
        OnChange = trckbrBrightChange
      end
      object trckbrContrast: TTrackBar
        Left = 112
        Top = 56
        Width = 166
        Height = 9
        Max = 100
        TabOrder = 5
        OnChange = trckbrContrastChange
      end
      object trckbrSaturation: TTrackBar
        Left = 112
        Top = 88
        Width = 166
        Height = 9
        Max = 100
        TabOrder = 6
        OnChange = trckbrSaturationChange
      end
      object trckbrDefinition: TTrackBar
        Left = 112
        Top = 120
        Width = 166
        Height = 9
        Max = 100
        TabOrder = 7
        OnChange = trckbrDefinitionChange
      end
    end
  end
end
