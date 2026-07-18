// DlgLedCtrl.cpp : implementation file
//

#include "stdafx.h"
#include "resource.h"
#include "DlgLedCtrl.h"


// CDlgLedCtrl dialog

IMPLEMENT_DYNAMIC(CDlgLedCtrl, CDialog)

CDlgLedCtrl::CDlgLedCtrl(CWnd* pParent /*=NULL*/)
	: CDialog(CDlgLedCtrl::IDD, pParent)
{
	m_pVzLPRClientCtrl	= NULL;
	m_lprHandle			= 0;
}

CDlgLedCtrl::~CDlgLedCtrl()
{
}

void CDlgLedCtrl::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_SLIDER_LEVEL, m_sliderLevel);
	DDX_Control(pDX, IDC_LBL_LEVEL, m_lblLevel);
}


BEGIN_MESSAGE_MAP(CDlgLedCtrl, CDialog)
	ON_BN_CLICKED(IDC_RDO_AUTO, &CDlgLedCtrl::OnBnClickedRdoAuto)
	ON_BN_CLICKED(IDC_RDO_OPEN, &CDlgLedCtrl::OnBnClickedRdoOpen)
	ON_BN_CLICKED(IDC_RDO_CLOSE, &CDlgLedCtrl::OnBnClickedRdoClose)
	ON_WM_HSCROLL()
END_MESSAGE_MAP()


// CDlgLedCtrl message handlers

BOOL CDlgLedCtrl::OnInitDialog()
{
	CDialog::OnInitDialog();

	if ( m_pVzLPRClientCtrl != NULL && m_lprHandle > 0 )
	{
		int level = m_pVzLPRClientCtrl->VzLPRGetLEDLightLevel(m_lprHandle);
		m_sliderLevel.SetRange(1, 7);
		m_sliderLevel.SetPos(level + 1);

		CString strLevel;
		strLevel.Format("%d¼¶", level + 1);
		m_lblLevel.SetWindowText(strLevel);

		int mode  = m_pVzLPRClientCtrl->VzLPRGetLEDLightControlMode(m_lprHandle);
		if ( mode == 0  )
		{
			((CButton *)GetDlgItem(IDC_RDO_AUTO))->SetCheck(TRUE);
		}
		else if( mode == 1 )
		{
			((CButton *)GetDlgItem(IDC_RDO_OPEN))->SetCheck(TRUE);
		}
		else
		{
			((CButton *)GetDlgItem(IDC_RDO_CLOSE))->SetCheck(TRUE);
		}
	}

	return TRUE;
}

void CDlgLedCtrl::SetVzlprclientctrl( CVzlprclientctrlctrl1 *pVzLPRClientCtrl, long lprHandle )
{
	m_pVzLPRClientCtrl	= pVzLPRClientCtrl;
	m_lprHandle			= lprHandle;
}

void CDlgLedCtrl::OnBnClickedRdoAuto()
{
	if( m_pVzLPRClientCtrl != NULL )
	{
		m_pVzLPRClientCtrl->VzLPRSetLEDLightControlMode(m_lprHandle, 0);
	}
}

void CDlgLedCtrl::OnBnClickedRdoOpen()
{
	if( m_pVzLPRClientCtrl != NULL )
	{
		m_pVzLPRClientCtrl->VzLPRSetLEDLightControlMode(m_lprHandle, 1);
	}
}

void CDlgLedCtrl::OnBnClickedRdoClose()
{
	if( m_pVzLPRClientCtrl != NULL )
	{
		m_pVzLPRClientCtrl->VzLPRSetLEDLightControlMode(m_lprHandle, 2);
	}
}

void CDlgLedCtrl::OnHScroll(UINT nSBCode, UINT nPos, CScrollBar* pScrollBar)
{
	int level = m_sliderLevel.GetPos();

	CString strLevel;
	strLevel.Format("%d¼¶", level);
	m_lblLevel.SetWindowText(strLevel);

	if( m_pVzLPRClientCtrl != NULL )
	{
		m_pVzLPRClientCtrl->VzLPRSetLEDLightLevel(m_lprHandle, level - 1);
	}

	CDialog::OnHScroll(nSBCode, nPos, pScrollBar);
}
