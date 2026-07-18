// DlgTriggerShow.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgTriggerShow.h"


// DlgTriggerShow dialog

IMPLEMENT_DYNAMIC(DlgTriggerShow, CDialog)

DlgTriggerShow::DlgTriggerShow(CWnd* pParent /*=NULL*/)
	: CDialog(DlgTriggerShow::IDD, pParent)
{
	InitializeCriticalSection(&m_csFrame);
}

DlgTriggerShow::~DlgTriggerShow()
{
	DeleteCriticalSection(&m_csFrame);
}

void DlgTriggerShow::SetFrame(const unsigned char *pFrame, unsigned uWidth, unsigned uHeight, unsigned uPitch)
{
	EnterCriticalSection(&m_csFrame);
	m_fBuffer.SetFrame(pFrame, uWidth, uHeight, uPitch);
	LeaveCriticalSection(&m_csFrame);

	int nCXFrame = GetSystemMetrics(SM_CXFRAME);
	int nCYFrame = GetSystemMetrics(SM_CYFRAME);
	int nCapH = GetSystemMetrics(SM_CYCAPTION);
//	m_dlgShow.MoveWindow(10, 10, nWinWidth + nCXFrame*2, nWinHeight + nCYFrame*2 + nCapH);
	SetWindowPos(NULL, 0, 0, uWidth + nCXFrame*2, uHeight + nCYFrame*2 + nCapH, SWP_NOMOVE);
	ShowWindow(SW_SHOW);

	Invalidate();

}

unsigned char *DlgTriggerShow::GetFrame(unsigned &uWidth, unsigned &uHeight, unsigned &uPitch)
{
	unsigned char *pRT = NULL;
	EnterCriticalSection(&m_csFrame);
	if(m_fBuffer.m_pBuffer)
	{
		pRT = m_fBuffer.m_pBuffer;
		uWidth = m_fBuffer.m_uWidth;
		uHeight = m_fBuffer.m_uHeight;
		uPitch = m_fBuffer.m_uWidth*3;
	}	
	LeaveCriticalSection(&m_csFrame);

	return(pRT);
}

void DlgTriggerShow::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
}


BEGIN_MESSAGE_MAP(DlgTriggerShow, CDialog)
	ON_WM_PAINT()
END_MESSAGE_MAP()


// DlgTriggerShow message handlers

BOOL DlgTriggerShow::OnInitDialog()
{
	CDialog::OnInitDialog();

	m_draw.InitHWND(GetSafeHwnd());

	return TRUE;  // return TRUE unless you set the focus to a control
	// EXCEPTION: OCX Property Pages should return FALSE
}

BOOL DlgTriggerShow::DestroyWindow()
{
	return CDialog::DestroyWindow();
}

void DlgTriggerShow::OnPaint()
{
	CPaintDC dc(this); // device context for painting
	// TODO: Add your message handler code here
	// Do not call CDialog::OnPaint() for painting messages
	EnterCriticalSection(&m_csFrame);

	m_draw.DrawRGB24(m_fBuffer.m_pBuffer, m_fBuffer.m_uWidth, m_fBuffer.m_uHeight);

	LeaveCriticalSection(&m_csFrame);
}
