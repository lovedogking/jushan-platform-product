// DlgGroupCfg.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgGroupCfg.h"
#include "DlgGroupEdit.h"
#include "VzString.h"


// CDlgGroupCfg dialog

IMPLEMENT_DYNAMIC(CDlgGroupCfg, CDialog)

CDlgGroupCfg::CDlgGroupCfg(CWnd* pParent /*=NULL*/)
	: CDialog(CDlgGroupCfg::IDD, pParent)
	, m_lblIP(_T(""))
{
	memset(&m_data, 0, sizeof(VZ_OVZID_RESULT));
	m_hLPRClient = NULL;
}

CDlgGroupCfg::~CDlgGroupCfg()
{
}

void CDlgGroupCfg::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Text(pDX, IDC_LBL_IP, m_lblIP);
	DDX_Control(pDX, IDC_GROUP_TREE, m_groupTree);
	DDX_Control(pDX, IDC_DEVICE_LIST, m_lstDevice);
}


BEGIN_MESSAGE_MAP(CDlgGroupCfg, CDialog)
	ON_BN_CLICKED(IDC_BTN_EDIT, &CDlgGroupCfg::OnBnClickedBtnEdit)
	ON_BN_CLICKED(IDC_BTN_DELE, &CDlgGroupCfg::OnBnClickedBtnDele)
	ON_BN_CLICKED(IDC_BTN_ADD, &CDlgGroupCfg::OnBnClickedBtnAdd)
END_MESSAGE_MAP()


// CDlgGroupCfg message handlers

BOOL CDlgGroupCfg::OnInitDialog()
{
	CDialog::OnInitDialog();

	m_groupTree.ModifyStyle(0, TVS_HASBUTTONS | TVS_LINESATROOT);

	m_lstDevice.InsertColumn( 0, "设备名称",  LVCFMT_LEFT, 120 );
	m_lstDevice.InsertColumn( 1, "设备IP",    LVCFMT_LEFT, 120 );
	m_lstDevice.InsertColumn( 2, "状态",	  LVCFMT_LEFT, 100 );
	m_lstDevice.InsertColumn( 3, "是否启用",  LVCFMT_LEFT, 100 );

	m_lstDevice.SetExtendedStyle(m_lstDevice.GetExtendedStyle() | LVS_EX_FULLROWSELECT);

	LoadGroupList( );

	return TRUE;
}

void CDlgGroupCfg::LoadGroupList( )
{
	m_groupTree.DeleteAllItems();
	m_lstDevice.DeleteAllItems();

	if ( m_hLPRClient != NULL)
	{
		memset(&m_data, 0, sizeof(VZ_OVZID_RESULT));
		int ret = VzLPRClient_GetOvzid(m_hLPRClient, &m_data);

		for ( int num = 0; num < m_data.uCount; num++ )
		{
			string sName = m_data.info[num].name;
			if ( sName.length() > 0 )
			{
				string value = "/";
				std::vector<std::string> names;
				CVzString::split(sName, value, &names);

				string sDeviceName;

				int nNameCount = names.size( );
				if ( nNameCount > 0  )
				{
					HTREEITEM itemGroup = FindTreeItem(m_groupTree.GetRootItem(), names[0].c_str());
					if ( itemGroup == NULL )
					{
						itemGroup = m_groupTree.InsertItem(names[0].c_str(), NULL);
					}

					for( int i = 1; i < nNameCount; i++ )
					{
						HTREEITEM itemSub = FindSubTreeItem(itemGroup, names[1].c_str());
						if ( itemSub != NULL ) {
							itemGroup = itemSub;
						}
						else {
							itemGroup = m_groupTree.InsertItem(names[i].c_str(), itemGroup);
						}
					}

					sDeviceName = names[nNameCount - 1];
				}

				MyExpandAll( m_groupTree.GetRootItem() );

				int nIndex = m_lstDevice.InsertItem(0, sDeviceName.c_str());
				m_lstDevice.SetItemText(nIndex, 1, m_data.info[num].ip_addr);
				m_lstDevice.SetItemData(nIndex, (DWORD_PTR)num);

				if( m_data.info[num].type == 1 )
				{
					m_lstDevice.SetItemText(nIndex, 2, "入口");
				}
				else if( m_data.info[num].type == 2 )
				{
					m_lstDevice.SetItemText(nIndex, 2, "出口");
				}
				else
				{
					m_lstDevice.SetItemText(nIndex, 2, "未组网");
				}

				if ( m_data.info[num].enable_group )
				{
					m_lstDevice.SetItemText(nIndex, 3, "是");
				}
				else
				{
					m_lstDevice.SetItemText(nIndex, 3, "否");
				}
			}	
		}
	}
}

void CDlgGroupCfg::MyExpandAll(HTREEITEM hTreeItem)
{
	MyExpandTree(hTreeItem);

	HTREEITEM hNextItem = m_groupTree.GetNextItem(hTreeItem, TVGN_NEXT);
	while( hNextItem != NULL )
	{
		MyExpandTree(hNextItem);
		hNextItem = m_groupTree.GetNextItem(hNextItem, TVGN_NEXT);
	}
}

void CDlgGroupCfg::SetLPRHandle( VzLPRClientHandle hLPRClient )
{
	m_hLPRClient = hLPRClient;
}

void CDlgGroupCfg::SetDeviceIP( CString strIP )
{
	m_lblIP = strIP;
}

void CDlgGroupCfg::MyExpandTree(HTREEITEM hTreeItem)
{
	if(!m_groupTree.ItemHasChildren(hTreeItem))
	{
		return;
	}

	HTREEITEM hChildItem = m_groupTree.GetChildItem(hTreeItem);
	while (hChildItem != NULL)
	{
		MyExpandTree(hChildItem);
		hChildItem = m_groupTree.GetNextItem(hChildItem, TVGN_NEXT);
	}
	m_groupTree.Expand(hTreeItem,TVE_EXPAND);

}

void CDlgGroupCfg::OnBnClickedBtnEdit()
{
	int nCurSel = GetSeleIndex( );
	if ( nCurSel >= 0 )
	{
		CString strName = m_lstDevice.GetItemText(nCurSel, 0);
		CString strIP   = m_lstDevice.GetItemText(nCurSel, 1);
		CString strType = m_lstDevice.GetItemText(nCurSel, 2);

		int nType = 0;
		if( strType == "入口" )
		{
			nType = 1;
		}
		else if( strType == "出口" )
		{
			nType = 2;
		}

		int index = (int)m_lstDevice.GetItemData(nCurSel);

		CDlgGroupEdit dlg;
		dlg.SetLPRHandle(m_hLPRClient);
		dlg.setMainHandle(this->m_pParentWnd->m_hWnd);
		dlg.SetOvzidResult(&m_data, index);
		dlg.SetDeviceInfo(strName, strIP, nType, m_data.info[index].sn, m_data.info[index].name, m_data.info[index].enable_group);
		if ( IDOK == dlg.DoModal() )
		{
			LoadGroupList( );
		}
	}
	else
	{
		MessageBox("请选择一条记录", "提示", MB_OK );
	}
}

int CDlgGroupCfg::GetSeleIndex( )
{
	int nCurSel = -1;

	for(int i=0; i< m_lstDevice.GetItemCount(); i++)
	{
		if( m_lstDevice.GetItemState(i, LVIS_SELECTED) == LVIS_SELECTED )
		{
			nCurSel = i;
			break;
		}
	}

	return nCurSel;
}

// 删除组网操作
void CDlgGroupCfg::OnBnClickedBtnDele()
{
	int nCurSel = GetSeleIndex( );
	if ( nCurSel >= 0 )
	{
		int index = (int)m_lstDevice.GetItemData(nCurSel);

		strcpy(m_data.info[index].name, "" );
		strcpy(m_data.info[index].original_name, "");
		m_data.info[index].type  = 0;

		int ret = 0; // VzLPRClient_EnableDevicegroup(m_hLPRClient, &m_data);
		if ( ret == 0 )
		{
			LoadGroupList( );

			MessageBox("删除成功!", "提示", MB_OK );
		}
		else
		{
			MessageBox("删除失败，请重试!", "提示", MB_OK );
		}
	}
	else
	{
		MessageBox("请选择一条记录", "提示", MB_OK );
	}
}

HTREEITEM CDlgGroupCfg::FindTreeItem( HTREEITEM firstNode, CString strText )
{
	HTREEITEM itemNode = NULL;

	if ( firstNode != NULL )
	{
		CString itemText = m_groupTree.GetItemText(firstNode);
		if ( itemText == strText )
		{
			itemNode = firstNode;
		}
		else
		{
			HTREEITEM hNextItem = m_groupTree.GetNextSiblingItem(firstNode);
			while (hNextItem != NULL)
			{
				CString itemText = m_groupTree.GetItemText(hNextItem);
				if ( itemText == strText )
				{
					itemNode = hNextItem;
					break;
				}

				hNextItem = m_groupTree.GetNextSiblingItem(hNextItem);
			}
		}
	}
	
	return itemNode;	
}

HTREEITEM CDlgGroupCfg::FindSubTreeItem( HTREEITEM parentNode, CString strText )
{
	HTREEITEM itemNode = NULL;

	if ( parentNode != NULL && m_groupTree.ItemHasChildren(parentNode) )
	{
		HTREEITEM hNextItem = m_groupTree.GetChildItem(parentNode);
		while (hNextItem != NULL)
		{
			CString itemText = m_groupTree.GetItemText(hNextItem);
			if ( itemText == strText )
			{
				itemNode = hNextItem;
				break;
			}

			hNextItem = m_groupTree.GetNextSiblingItem(hNextItem);
		}
	}

	return itemNode;
}

// 添加组网的功能
void CDlgGroupCfg::OnBnClickedBtnAdd()
{
	UpdateData(TRUE);

	CDlgGroupEdit dlg;
	dlg.SetLPRHandle(m_hLPRClient);
	dlg.setMainHandle(this->m_pParentWnd->m_hWnd);
	VZ_DEV_SERIAL_NUM sn = {0};
	int ret = VzLPRClient_GetSerialNumber(m_hLPRClient, &sn);
	if ( ret == 0 )
	{
		char strSN[64];
		sprintf_s(strSN, 64, "%08X-%08X", sn.uHi, sn.uLo);

		dlg.SetOvzidResult(&m_data, 0);
		dlg.SetDeviceInfo(m_lblIP, m_lblIP, 0, strSN, "", false);
	}
	
	if ( IDOK == dlg.DoModal() )
	{
		LoadGroupList( );
	}
}
