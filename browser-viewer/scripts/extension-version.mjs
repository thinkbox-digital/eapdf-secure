// Builds use three numeric parts; legacy beta versions remain readable for updates.
export function extensionVersion(version) {
  const match = /^(\d+)\.(\d+)\.(\d+)(?:-beta\.(\d+))?$/.exec(version);
  if (!match) throw new Error(`Unsupported viewer version: ${version}`);
  const parts = match.slice(1).filter(value => value !== undefined);
  if (parts.some(value => String(Number(value)) !== value || Number(value) > 65535))
    throw new Error(`Invalid browser version: ${version}`);
  return parts.join('.');
}
export function advanceMinorVersion(version) {
  extensionVersion(version);
  const match=/^(\d+)\.(\d+)\.\d+(-beta\.\d+)?$/.exec(version);
  const next=`${match[1]}.${Number(match[2])+1}.0`;
  extensionVersion(next);return next;
}
