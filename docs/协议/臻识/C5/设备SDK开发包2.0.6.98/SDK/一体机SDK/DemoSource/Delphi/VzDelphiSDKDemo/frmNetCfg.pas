unit frmNetCfg;

interface

uses
  Windows, Messages, SysUtils, Variants, Classes, Graphics, Controls, Forms,
  Dialogs, StdCtrls, VzLPRSDK;

type
  TFormNetCfg = class(TForm)
    lbl1: TLabel;
    lbl2: TLabel;
    lbl3: TLabel;
    edtIP: TEdit;
    edtMask: TEdit;
    edtGateWay: TEdit;
    btnNetCfgOK: TButton;
    procedure SetNetParam(strIP: string; SL, SH: LongWord);
    procedure btnNetCfgOKClick(Sender: TObject);
  private
    { Private declarations }
  public
    { Public declarations }
  end;

var
  FormNetCfg: TFormNetCfg;
  m_strIP : string;
  m_nSL, m_nSH : LongWord;
implementation

{$R *.dfm}
//获取网络参数
procedure TFormNetCfg.SetNetParam(strIP:string; SL:LongWord; SH:LongWord);
begin
  m_strIP := strIP;
  m_nSL := SL;
  m_nSH := SH;

  edtIP.Text := m_strIP;
end;

//设置修改网络参数
procedure TFormNetCfg.btnNetCfgOKClick(Sender: TObject);
var
  strIP, strNetMask, strGateway : string;
  ret : Integer;
begin
  strIP := edtIP.Text;
  strNetMask := edtMask.Text;
  strGateway := edtGateWay.Text;

  ret := VzLPRClient_UpdateNetworkParam(m_nSH, m_nSL, PChar(strIP), PChar(strGateway), PChar(strNetMask)); //修改网络参数
  if (ret = 2) then
  begin
    ShowMessage('设备IP跟网关不在同一网段，请重新输入!');
  end
  else
  begin
    if (ret = -1) then
    begin
      ShowMessage('修改网络参数失败，请重新输入!');
    end
    else
    begin
      ShowMessage('修改网络参数成功!');
    end;
  end;
end;

end.
