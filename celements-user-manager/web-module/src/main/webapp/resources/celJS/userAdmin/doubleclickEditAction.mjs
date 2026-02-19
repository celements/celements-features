const editUser = (li, event) => {
  const url = li.querySelector('.column_edit a').href;
  window.location.href = url;  
};

document.querySelectorAll('li.struct_table_row').forEach((li) => {
  li.addEventListener('dblclick', (event) => (editUser(li, event)));
});
