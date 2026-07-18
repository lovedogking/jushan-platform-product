// DlgVoice.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgVoice.h"

void CALLBACK gVZLPRDEV_CallBackSaveCallData(int iTalkHandle, const void *pRecvDataBuffer,
										  DWORD dwBufSize, BYTE byAudioFlag, DWORD dwUser)
{
	WaveFile **pWF = (WaveFile **)dwUser;
	if(*pWF != NULL)
	{
		WaveFileWrite(*pWF, pRecvDataBuffer, dwBufSize);
	}
}

// CDlgVoice dialog

IMPLEMENT_DYNAMIC(CDlgVoice, CDialog)

CDlgVoice::CDlgVoice(CWnd* pParent /*=NULL*/)
	: CDialog(CDlgVoice::IDD, pParent)
	, m_bChkWaveFile(FALSE)
	, m_strWavPath(_T(""))
{
	m_nTalkHandle  = -1;
	m_nSoundHandle = -1;
	m_pWaveFile	   = NULL;

}

CDlgVoice::~CDlgVoice()
{
}

void CDlgVoice::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Check(pDX, IDC_CHK_WAVE_FILE, m_bChkWaveFile);
	DDX_Text(pDX, IDC_EDIT_PATH, m_strWavPath);
}


BEGIN_MESSAGE_MAP(CDlgVoice, CDialog)
	ON_BN_CLICKED(IDC_BTN_CALL, &CDlgVoice::OnBnClickedBtnCall)
	ON_BN_CLICKED(IDC_BTN_PLAY_VOICE, &CDlgVoice::OnBnClickedBtnPlayVoice)
	ON_BN_CLICKED(IDC_BTN_STOP_VOICE, &CDlgVoice::OnBnClickedBtnStopVoice)
	ON_WM_CLOSE()
	ON_BN_CLICKED(IDC_BTN_BROWSE, &CDlgVoice::OnBnClickedBtnBrowse)
END_MESSAGE_MAP()


// CDlgVoice message handlers
// CDlgNetwork message handlers
void CDlgVoice::SetLPRHandle( VzLPRClientHandle hLPRClient )
{
	m_hLPRClient = hLPRClient;
}

BOOL CDlgVoice::OnInitDialog()
{
	CDialog::OnInitDialog();

	// TODO:  Add extra initialization here

	return TRUE;  // return TRUE unless you set the focus to a control
	// EXCEPTION: OCX Property Pages should return FALSE
}

// 喊话功能
void CDlgVoice::OnBnClickedBtnCall()
{
	if ( m_nTalkHandle > 0  )
	{
		m_nTalkHandle = -1;
	}

	UpdateData( TRUE );


	if( m_bChkWaveFile )
	{
		//char szModuleDir[MAX_PATH] = {0};
		//GetModuleFileName(NULL, szModuleDir, MAX_PATH);
		//char* p = strrchr(szModuleDir,'\\');
		//*p = '\0';

		////初始化结果输出窗口
		//char szWavPath[MAX_PATH] = {0};
		//sprintf_s( szWavPath, "%s\\alarm.wav", szModuleDir );

		if( m_strWavPath.GetLength() > 0 )
		{
		}
		else
		{
			MessageBox("请选择语音文件!", "提示", MB_OK);
		}

		
	}
	else
	{
	}	
}

// 一体机向客户端发声
void CDlgVoice::OnBnClickedBtnPlayVoice()
{
	if( m_nSoundHandle > 0 )
	{
		m_nSoundHandle = -1;
	}
}

void CDlgVoice::OnBnClickedBtnStopVoice()
{
	if( m_nSoundHandle > 0 )
	{
		m_nSoundHandle = -1;
	}
}

void CDlgVoice::OnClose()
{
	if ( m_nTalkHandle > 0  )
	{
		m_nTalkHandle = -1;
	}

	OnBnClickedBtnStopVoice( );

	CDialog::OnClose();
}

void CDlgVoice::OnBnClickedBtnBrowse()
{

	CFileDialog FileDlg (TRUE,NULL,NULL,OFN_HIDEREADONLY|OFN_OVERWRITEPROMPT,"wav文件|*.wav||");
	if (FileDlg.DoModal () == IDOK)
	{
		m_strWavPath = FileDlg.GetPathName( );
	}

	UpdateData(FALSE);
}
