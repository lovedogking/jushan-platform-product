unit frmPlayVoice;

interface

uses
  Windows, Messages, SysUtils, Variants, Classes, Graphics, Controls, Forms,
  Dialogs, VzLPRSDK, StdCtrls;

type
  TFormPlayVoice = class(TForm)
    btnVoicePlay: TButton;
    edtVoiceText: TEdit;
    procedure SetLPRHandle(mHandle: Integer);
    procedure btnVoicePlayClick(Sender: TObject);
  private
    { Private declarations }
  public
    { Public declarations }
  end;

var
  FormPlayVoice: TFormPlayVoice;
  m_hLPRClient: Integer;

implementation

{$R *.dfm}
procedure TFormPlayVoice.SetLPRHandle(mHandle: Integer);
begin
  m_hLPRClient := mHandle; //获取打开设备句柄
end;

procedure TFormPlayVoice.btnVoicePlayClick(Sender: TObject);
var
  ret: Integer;
  sContent: string;
begin
  if m_hLPRClient <> 0 then
  begin
    sContent:= edtVoiceText.Text;
    ret := VzLPRClient_PlayVoice(m_hLPRClient, PChar(sContent), 0, 100, 1);
  end;
end;

end.
