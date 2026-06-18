import { useContext } from "react";
import { ProjectContext, type ProjectContextValue } from "./ProjectProvider";

const FALLBACK: ProjectContextValue = {
  project: null,
  loading: false,
  error: null,
  refresh: () => {},
};

export function useProject(): ProjectContextValue {
  const ctx = useContext(ProjectContext);
  return ctx ?? FALLBACK;
}
