// DlgPlayVoice.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgPlayVoice.h"


// CDlgPlayVoice dialog

IMPLEMENT_DYNAMIC(CDlgPlayVoice, CDialog)

CDlgPlayVoice::CDlgPlayVoice(CWnd* pParent /*=NULL*/)
	: CDialog(CDlgPlayVoice::IDD, pParent)
	, m_strContent(_T(""))
{
	m_hLPRClient = NULL;
}

CDlgPlayVoice::~CDlgPlayVoice()
{
}

void CDlgPlayVoice::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Text(pDX, IDC_EDIT_VOICE, m_strContent);
}


BEGIN_MESSAGE_MAP(CDlgPlayVoice, CDialog)
	ON_BN_CLICKED(IDC_BTN_PLAY_VOICE, &CDlgPlayVoice::OnBnClickedBtnPlayVoice)
END_MESSAGE_MAP()


// CDlgPlayVoice message handlers

// ≤•∑≈”Ô“Ù
void CDlgPlayVoice::OnBnClickedBtnPlayVoice()
{
	UpdateData(TRUE);

	if ( m_hLPRClient != NULL )
	{
		int ret = VzLPRClient_PlayVoice(m_hLPRClient, m_strContent.GetBuffer(0), 0, 100, 1);
	}
}

// CDlgStore message handlers
void CDlgPlayVoice::SetLPRHandle(VzLPRClientHandle hLPRClient)
{
	m_hLPRClient = hLPRClient;
}
